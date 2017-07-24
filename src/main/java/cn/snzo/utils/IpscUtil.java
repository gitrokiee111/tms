package cn.snzo.utils;

import cn.snzo.common.Constants;
import cn.snzo.entity.Conference;
import cn.snzo.entity.ConferenceRoom;
import cn.snzo.entity.Contact;
import cn.snzo.entity.Recording;
import cn.snzo.repository.ConferenceRepository;
import cn.snzo.repository.ConferenceRoomRepository;
import cn.snzo.repository.RecordingRepository;
import cn.snzo.ws.ChangeReminder;
import com.hesong.ipsc.ccf.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by chentao on 2017/7/14 0014.
 */
@Component
public class IpscUtil {

    private static Logger logger = LoggerFactory.getLogger(IpscUtil.class);

    private static ConferenceRepository conferenceRepository;


    @Autowired
    public void setConferenceRepository(ConferenceRepository conferenceRepository) {
        IpscUtil.conferenceRepository = conferenceRepository;
    }

    private static RecordingRepository  recordingRepository;

    @Autowired
    public void setRecordingRepository(RecordingRepository recordingRepository) {
        IpscUtil.recordingRepository = recordingRepository;
    }

    private static ConferenceRoomRepository conferenceRoomRepository;

    @Autowired
    public  void setConferenceRoomRepository(ConferenceRoomRepository conferenceRoomRepository) {
        IpscUtil.conferenceRoomRepository = conferenceRoomRepository;
    }

    private static ChangeReminder changeReminder;

    @Autowired
    public void setChangeReminder(ChangeReminder changeReminder) {
        IpscUtil.changeReminder = changeReminder;
    }

    public static final String VOIP = "10.1.2.152";

    //    private static final String ipscIpAddr = "192.168.2.100"; /// IPSC 服务器的内网地址
    private static final String              ipscIpAddr  = "127.0.0.1"; /// IPSC 服务器的内网地址
    private static final byte                localId     = 24;
    private static final byte                commanderId = 10;
    public static        Commander           commander   = null;
    public static        BusAddress          busAddress  = null;
    public static        Map<String, String> callConfMap = new HashMap<>();
    public static        Map<String, String> callPhoneMap = new HashMap<>();
    public static        Map<String, Integer> callEnterDtfmCount = new HashMap<>(); //每个呼入呼叫最多输入三次密码


    //    @Override
    public static void init() throws InterruptedException {
        logger.info(">>>>>>>>> Data Bus 客户端单元初始化");
        Unit.initiate(localId, new UnitCallbacks() {
            public void connectSucceed(Client client) {
                logger.info(">>>>>>>>> 成功的连接到了IPSC服务程序的 Data Bus");
                busAddress = new BusAddress(commander.getConnectingUnitId(), (byte) 0);
            }

            public void connectFailed(Client client, int i) {
                System.out.format("[{}] 连接失败", client.getId());
            }

            public void connectLost(Client client) {
                System.out.format("[{}] 连接丢失", client.getId());
            }

            public void globalConnectStateChanged(byte b, byte b1, byte b2, byte b3, String s) {
            }
        });

        Thread.sleep(10000);
        //创建commander
        logger.info(">>>>>>>>> 初始化commander");
        createCommander();
    }


    public static void createCommander() throws InterruptedException {
        /// 新建一个命令发送者
        commander = Unit.createCommander(
                commanderId,
                ipscIpAddr,
                // 事件监听器
                new RpcEventListener() {
                    public void onEvent(BusAddress busAddress, RpcRequest rpcRequest) {
                        String fullMethodName = rpcRequest.getMethod();
                        if (fullMethodName.startsWith("sys.call")) {
                            /// 呼叫事件
                            String methodName = fullMethodName.substring(9);
                            final String callId = (String) rpcRequest.getParams().get("res_id");
                            if (methodName.equals("on_released")) {
                                logger.warn(">>>>>>>>> 呼叫 {} 已经释放", callId);
                                String confId = callConfMap.get(callId);
                                if (confId != null) {
                                    try {
                                        changeReminder.sendMessageToAll(confId);
                                    } catch (IOException e) {
                                        logger.error(">>>>>>>>> 发送socket错误");
                                    }
                                }
                                //将呼叫从缓存中清除
                                callConfMap.remove(callId);

                            } else if (methodName.equals("on_ringing")) {
                                logger.info(">>>>>>>>> 呼叫 {} 振铃", callId);
                            } else if (methodName.equals("on_dial_completed")) {
                                String error = (String) rpcRequest.getParams().get("error");
                                if (error == null) {
                                    String confId = callConfMap.get(callId);
                                    if (confId != null) {
                                        logger.info(">>>>>>>>> 呼叫 {} 拨号成功，操作呼叫资源，让它加入会议 {} ...", callId, confId);
                                        addCallToConf(callId, confId);
                                    }
                                } else {
                                    logger.error(">>>>>>>>> 呼叫 {} 拨号失败：{}", callId, error);
                                }
                            } else if (methodName.equals("on_record_completed")) {
                                String error = (String) rpcRequest.getParams().get("error");
                                logger.info(">>>>>>>>> 录音停止callId: {} error: {}", callId, error);
                            } else if (methodName.equals("on_incoming")) {
                                String error = (String) rpcRequest.getParams().get("error");
                                if (error != null) {
                                    logger.error(">>>>>>>>> 呼入呼叫错误，callId ={}", callId);
                                } else {
                                    logger.warn(">>>>>>>>> 呼入呼叫，callId ={}", callId);
                                    logger.info(">>>>>>>>> 呼入呼叫参数:{}", rpcRequest.getParams().get("params"));
                                    answer(callId);
                                }

                            } else if (methodName.equals("on_receive_dtmf_completed")) {
                                String error = (String) rpcRequest.getParams().get("error");
                                if (error == null) {
                                    String keys = (String)rpcRequest.getParams().get("keys");
                                    logger.info(">>>>>>>>>接收到dtmf码为：{}", keys);
                                    ConferenceRoom conferenceRoom = conferenceRoomRepository.findByIvrPassword(keys);
                                    boolean isRight = false;
                                    Conference conference = null;
                                    if (conferenceRoom != null)
                                    {
                                        //查询该会议室中正在进行的会议
                                        conference = conferenceRepository.findByRoomIdAndStatus(conferenceRoom.getId(), 1);
                                        if (conference != null) {
                                            isRight  = true;
                                        } else{
                                            logger.error(">>>>>>>>>该会议室无正在进行的会议,播放语音{}", Constants.CLOSED_VOICE);
                                            try {
                                                Thread.sleep(6000);
                                            } catch (InterruptedException e) {
                                                logger.error(">>>>>>>>>播放语音{}被中断", Constants.READY_VOICE);
                                            }
                                            playContent(callId, Constants.CLOSED_VOICE);
                                        }
                                    }
                                    if (isRight) {
                                        logger.info(">>>>>>>>>接收到的dtmf码与会议室ivr码相同,播放欢迎语音{}", Constants.READY_VOICE);

                                        final Conference finalConference = conference;
                                        playContent(callId, Constants.READY_VOICE, new RpcResultListener() {
                                            @Override
                                            protected void onResult(Object o) {
                                                try {
                                                    Thread.sleep(6000);
                                                } catch (InterruptedException e) {
                                                    logger.error(">>>>>>>>>播放语音{}被中断", Constants.READY_VOICE);
                                                }
                                                logger.info(">>>>>>>>>将该呼叫{}加入会议{}", callId, finalConference.getResId());
                                                addCallToConf(callId, finalConference.getResId());
                                            }

                                            @Override
                                            protected void onError(RpcError rpcError) {
                                                logger.error(">>>>>>>>> 播放语音错误,code={},message={}", rpcError.getCode(), rpcError.getMessage());
                                            }

                                            @Override
                                            protected void onTimeout() {

                                            }
                                        });

                                    } else {
                                        logger.info(">>>>>>>>>接收到的dtmf码错误，播放错误提示音");
                                        Integer count = callEnterDtfmCount.get(callId);
                                        playWrongVoice(callId, count);
                                    }

                                }
                            } else if (methodName.equals("on_answer_completed")) {
                                logger.warn(">>>>>>>>> 呼入呼叫成功被接听，callId ={}", callId);
                                String error = (String) rpcRequest.getParams().get("error");
                                if (error != null) {
                                    logger.error(">>>>>>>>> 接听呼入呼叫{}发生错误", callId);
                                } else {
                                    logger.info(">>>>>>>>> 成功接听呼入呼叫{}，开始接收dtmf码");
                                    callReceiveDtmfStart(callId, Constants.WELCOME_VOICE);
                                }
                            }
                        } else if (fullMethodName.startsWith("sys.conf")) {
                            /// 会议事件
                            String methodName = fullMethodName.substring(9);
                            String confId = (String) rpcRequest.getParams().get("res_id");
                            if (methodName.equals("on_released")) {
                                logger.warn(">>>>>>>>> 会议 {} 已经释放", confId);
                                //会议结束，推送通知消息
                                try {
                                    logger.warn(">>>>>>>>> 推送会议{}结束通知消息", confId);
                                    changeReminder.sendMessageToAll(confId);
                                } catch (IOException e) {
                                    logger.error(">>>>>>>>> 推送通知消息错误");
                                }
                                //修改为已结束状态
                                Conference conference = conferenceRepository.findByResId(confId);
                                conference.setStatus(2);
                                conferenceRepository.save(conference);
                            } else if (methodName.equals("on_record_completed")) {
                                logger.warn(">>>>>>>>> 会议 {} 录音已结束", confId);
                                String error = (String) rpcRequest.getParams().get("error");
                                if (error == null) {
                                    logger.warn(">>>>>>>>> 保存会议 {} 录音信息", confId);
                                    Recording recording = new Recording();
                                    Conference conference = conferenceRepository.findByResId(confId);
                                    if (conference != null) {
                                        recording.setConferenceId(conference.getId());
                                        recording.setConferenceNo(conference.getResId());
                                        String filename = (String) rpcRequest.getParams().get("record_file");
                                        logger.info(">>>>>>>>> 会议{}录音保存绝对路径：{}",conference.getResId(), filename);
                                        int index = filename.lastIndexOf("/");
                                        if (index != -1) {
                                            recording.setFilename(filename.substring(index + 1));
                                            int length = Constants.SFTP_PATH.length();
                                            //保存的是sftp目录下的子目录
                                            String filepath = filename.substring(length, index);
                                            logger.info(">>>>>>>>> 会议{}录音保存相对sftp路径：{}",conference.getResId(), filepath);
                                            recording.setFilePath(filepath);
                                        }

                                        Integer start = (Integer) rpcRequest.getParams().get("begin_time");
                                        Integer end = (Integer) rpcRequest.getParams().get("end_time");
                                        recording.setStartTime(new Date((long)start * 1000));
                                        recording.setEndTime(new Date((long)end * 1000));
                                        recording.setRoomId(conference.getRoomId());
                                        recording.setRoomNo(conference.getRoomNo());
                                        recordingRepository.save(recording);
                                    }
                                }
                            }
                        }
                    }
                });
    }


    public static void addCallToConf(String callId, String conferenceId) {
        try {
            Map<String, Object> params = new HashMap<String, Object>();
            params.put("res_id", callId);
            params.put("conf_res_id", conferenceId);
            params.put("max_seconds", Constants.MAX_CONF_SECONDS);
            commander.operateResource(
                    busAddress,
                    callId,
                    "sys.call.conf_enter",
                    params,
                    new RpcResultListener() {
                        @Override
                        protected void onResult(Object o) {
                            logger.info(">>>>>>>>> 呼叫 {} 加入会议 {} 操作完毕", callId, conferenceId);
                            callConfMap.put(callId, conferenceId);
                            //往前端推送socket消息
                            try {
                                logger.info(">>>>>>>>> 推送websocket通知消息confId=", conferenceId);
                                changeReminder.sendMessageToAll(conferenceId);
                            } catch (IOException e) {
                                logger.error(">>>>>>>>> 推送websocket通知消息错误 {}", conferenceId);
                            }
                        }

                        @Override
                        protected void onError(RpcError rpcError) {
                            logger.error(">>>>>>>>> 呼叫 {} 加入会议 {} 操作错误: {}", callId, conferenceId, rpcError.getMessage());
                        }

                        @Override
                        protected void onTimeout() {
                            logger.error(">>>>>>>>> 呼叫 {} 加入会议 {} 操作超时无响应", callId, conferenceId);
                        }
                    }
            );
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    public static void callOut(List<Contact> contacts, String ip, RpcResultListener listener) throws IOException, InterruptedException {
        for (Contact te : contacts) {
            Map<String, Object> params = new HashMap<String, Object>();
            params.put("to_uri", te.getPhone()+"@"+ip); /// 被叫号码的 SIP URI
            params.put("max_answer_seconds", Constants.MAX_ANSWER_SECONDS); /// 该呼叫最长通话允许时间
            String name = te.getName();
            logger.info(">>>>>>>>> Name ={} ", name);
            String reencode = new String(name.getBytes("gbk"),"utf-8");
            logger.info(">>>>>>>>> reEncode Name ={} ", reencode);
            params.put("user_data", te.getPhone()+"-"+ te.getPhone()); ///用户信息
            logger.info(">>>>>>>>> 呼叫参数： {}", params);
            commander.createResource(
                    busAddress,
                    "sys.call",
                    params,
                    listener
            );
        }
    }


    public static void createConference(Map<String, Object> params, RpcResultListener rpcResultListener) throws IOException {
        logger.info(">>>>>>>>> createConference params {}, busAddress {}", params, busAddress);
        if (commander != null) {
            commander.createResource(
                    busAddress,
                    "sys.conf",
                    params,
                    rpcResultListener);
        } else {
            logger.info(">>>>>>>>> commander客户端 未初始化");
        }
    }


    public static void stopConference(String confId, RpcResultListener listener) throws IOException {
        if (commander != null) {
            commander.operateResource(
                    busAddress,
                    confId,
                    "sys.conf",
                    null,
                    listener);
        } else {
            logger.info(">>>>>>>>> commander客户端 未初始化");
        }
    }


    public static void changePartMode(String confId, String callId, int mode, RpcResultListener listener) throws IOException {
        if (commander != null) {
            Map<String, Object> params = new HashMap<>();
            params.put("res_id", confId);
            params.put("call_res_id", callId);
            params.put("mode", mode);
            logger.info(">>>>>>>>> 修改与会者声音收放模式，confid={},callId={},mode={}", confId, callId, mode);
            commander.operateResource(
                    busAddress,
                    confId,
                    "sys.conf.set_part_voice_mode",
                    params,
                    listener);
        } else {
            logger.info(">>>>>>>>> commander客户端 未初始化");
        }
    }

    public static void exitConferece(String confId, String callId, RpcResultListener listener) throws IOException {
        if (commander != null) {
            Map<String, Object> params = new HashMap<>();
            params.put("res_id", confId);
            params.put("call_res_id", callId);
            logger.info(">>>>>>>>> 退出会议，confid={},callId={}", confId, callId);
            commander.operateResource(
                    busAddress,
                    callId,
                    "sys.call.conf_exit",
                    params,
                    listener);
        } else {
            logger.info(">>>>>>>>> commander客户端 未初始化");
        }
    }


    public static void dropCall(String callId, RpcResultListener listener) throws IOException {
        if (commander == null) {
            logger.info(">>>>>>>>> commander客户端 未初始化");
            return;
        }

        Map<String, Object> params = new HashMap<>();
        params.put("res_id", callId);
        logger.info(">>>>>>>>> 挂断呼叫,callId={}", callId);
        commander.operateResource(
                busAddress,
                callId,
                "sys.call.drop",
                params,
                listener);

    }



    public static void startRecord(String conferenceId, String path, RpcResultListener listener) throws IOException {
        if (commander != null) {
            Map<String, Object> params = new HashMap<>();
            params.put("res_id", conferenceId);
            params.put("max_seconds", Constants.MAX_CONF_SECONDS);
            params.put("record_file", path);
            params.put("record_format", 3);
            commander.operateResource(
                    busAddress,
                    conferenceId,
                    "sys.conf.record_start",
                    params,
                    listener);
        } else {
            logger.info(">>>>>>>>> commander客户端 未初始化");
        }

    }

    public static void stopRecord(String conferenceId, RpcResultListener listener) throws IOException {
        if (commander != null) {
            Map<String, Object> params = new HashMap<>();
            params.put("res_id", conferenceId);
            commander.operateResource(
                    busAddress,
                    conferenceId,
                    "sys.conf.record_stop",
                    params,
                    listener);
        } else {
            logger.info(">>>>>>>>> commander客户端 未初始化");
        }
    }

    public static void getConfParts(String confResId, RpcResultListener listener) throws IOException {
        if (commander != null) {
            Map<String, Object> params = new HashMap<>();
            params.put("res_id", confResId);
            commander.operateResource(
                    busAddress,
                    confResId,
                    "sys.conf.get_parts",
                    params,
                    listener);
        } else {
            logger.info(">>>>>>>>> commander客户端 未初始化");
        }
    }

    public static void checkConf(String confResId, RpcResultListener listener) throws IOException {
        if (commander == null) {
            logger.info(">>>>>>>>> commander客户端 未初始化");
            return;
        }
        Map<String, Object> params = new HashMap<>();
        params.put("res_id", confResId);
        commander.operateResource(
                busAddress,
                confResId,
                "sys.conf.exists",
                params,
                listener);

    }

    public static void checkCall(String callId, RpcResultListener listener) throws IOException {
        if (commander == null) {
            logger.info(">>>>>>>>> commander客户端 未初始化");
            return;
        }
        Map<String, Object> params = new HashMap<>();
        params.put("res_id", callId);
        commander.operateResource(
                busAddress,
                callId,
                "sys.call.exists",
                params,
                listener);
    }


    public static void playWrongVoice(String callId, Integer count) {

        //如果该呼叫输入错误2次，播放提示音 final_wrong_passwd.wav
        if (count == null) {
            logger.info(">>>>>>>>> 输入错误1次，呼叫id={}", callId);
            callReceiveDtmfStart(callId, Constants.WRONG_PASSWORD);
            callEnterDtfmCount.put(callId, 1);
        } else if (count == 1){
            logger.info(">>>>>>>>> 输入错误2次，呼叫id={}", callId);
            callReceiveDtmfStart(callId, Constants.WRONG_PASSWORD);
            callEnterDtfmCount.put(callId, ++count);
        } else if (count == 2) {
            logger.info(">>>>>>>>> 输入错误达到3次，呼叫id={}", callId);
            playContent(callId, Constants.FINAL_WRONG_PASSWORD, new RpcResultListener() {
                @Override
                protected void onResult(Object o) {
                    logger.info(">>>>>>>>> 操作呼叫{}放音成功", callId);
                    logger.info(">>>>>>>>> 将该呼叫挂断");
                    logger.info(">>>>>>>>> 等待播放");
                    try {
                        Thread.sleep(6000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    drop(callId);
                }

                @Override
                protected void onError(RpcError rpcError) {
                    logger.info(">>>>>>>>> 操作呼叫{}放音失败code={}, msg={}", callId, rpcError.getCode(), rpcError.getMessage());
                }

                @Override
                protected void onTimeout() {
                    logger.info(">>>>>>>>> 操作呼叫{}放音成功", callId);
                }
            });
            //从缓存中清除
            callEnterDtfmCount.remove(callId);
        }
    }

    public static void drop(String callId) {
        logger.info("挂断呼叫{}", callId);
        Map<String, Object> params = new HashMap<>();
        params.put("res_id", callId);
        try {
            commander.operateResource(
                    busAddress,
                    callId,
                    "sys.call.drop",
                    params,
                    new RpcResultListener() {
                        @Override
                        protected void onResult(Object o) {
                            logger.info(">>>>>>>>> 挂断呼叫{}已拒接", callId);
                        }

                        @Override
                        protected void onError(RpcError rpcError) {
                            logger.info(">>>>>>>>> 挂断呼叫{}失败,code={},msg={}", callId, rpcError.getCode(), rpcError.getMessage());
                        }

                        @Override
                        protected void onTimeout() {
                            logger.info(">>>>>>>>> 挂断呼叫{}超时", callId);
                        }
                    }
            );
        } catch (IOException e) {
            logger.error(">>>>>>>>> 挂断呼叫{}异常", callId);
        }
    }



    public static void reject(String callId) {
        logger.info("拒接呼叫{}", callId);
        Map<String, Object> params = new HashMap<>();
        params.put("res_id", callId);
        params.put("cause", "max wrong enter dtmf");
        try {
            commander.operateResource(
                    busAddress,
                    callId,
                    "sys.call.reject",
                    params,
                    new RpcResultListener() {
                        @Override
                        protected void onResult(Object o) {
                            logger.info(">>>>>>>>> 呼叫{}已拒接", callId);
                        }

                        @Override
                        protected void onError(RpcError rpcError) {
                            logger.info(">>>>>>>>> 呼叫{}拒接失败,code={},msg={}", callId, rpcError.getCode(), rpcError.getMessage());
                        }

                        @Override
                        protected void onTimeout() {
                            logger.info(">>>>>>>>> 呼叫{}拒接超时", callId);
                        }
                    }
            );
        } catch (IOException e) {
            logger.error(">>>>>>>>> 拒接呼叫{}异常", callId);
        }
    }


    public static void playContent(String callId, String filename) {
        playContent(callId, filename, new RpcResultListener() {
            @Override
            protected void onResult(Object o) {
                logger.info(">>>>>>>>> 操作呼叫{}放音成功", callId);
            }

            @Override
            protected void onError(RpcError rpcError) {
                logger.info(">>>>>>>>> 操作呼叫{}放音失败", callId);
            }

            @Override
            protected void onTimeout() {
                logger.info(">>>>>>>>> 操作呼叫{}放音超时", callId);
            }
        });
    }


    public static void playContent(String callId, String filename, RpcResultListener rpcResultListener) {
        if (commander == null) {
            logger.info(">>>>>>>>> commander客户端 未初始化");
            return;
        }
        try {
            Map<String, Object> params = new HashMap<>();
            params.put("res_id", callId);
            params.put("content", filename);
            commander.operateResource(
                    busAddress,
                    callId,
                    "sys.call.play_start",
                    params,
                    rpcResultListener);
        } catch (IOException e) {
            logger.info(">>>>>>>>> 播放声音失败");
        }

    }


    public static void answer(String callId) {
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("max_answer_seconds", 10000);
        params.put("res_id", callId);
        logger.warn("应答呼入呼叫参数，params ={}", params);
        try {
            commander.operateResource(
                    busAddress,
                    callId,
                    "sys.call.answer",
                    params,
                    new RpcResultListener() {
                        @Override
                        protected void onResult(Object o) {
                            logger.info(">>>>>>>>> 应答呼入 {} 操作完毕", callId);
                        }

                        @Override
                        protected void onError(RpcError rpcError) {
                            logger.error(">>>>>>>>> 应答呼入{} 操作错误: {}", callId, rpcError.getMessage());
                        }

                        @Override
                        protected void onTimeout() {
                            logger.error(">>>>>>>>> 应答呼入{} 操作超时无响应", callId);
                        }
                    }
            );
        } catch (IOException e) {
            logger.error(">>>>>>>>> 应答呼入发生异常", e);
        }

    }


    public static void callReceiveDtmfStart(String callId, String filename) {
        try {
            Map<String, Object> paramsDtmfStart = new HashMap<String, Object>();
            paramsDtmfStart.put("res_id", callId);
            paramsDtmfStart.put("play_content", filename);
            commander.operateResource(
                    busAddress,
                    callId,
                    "sys.call.receive_dtmf_start",
                    paramsDtmfStart,
                    new RpcResultListener() {
                        @Override
                        protected void onResult(Object o) {
                            logger.info(">>>>>>>>> 开始接收DTMF码， callId={}, params={}", callId, paramsDtmfStart);
                        }

                        @Override
                        protected void onError(RpcError rpcError) {
                            logger.info(">>>>>>>>> 开始接收DTMF码发生错误， callId={}, params={}", callId, paramsDtmfStart);
                        }

                        @Override
                        protected void onTimeout() {
                            logger.info(">>>>>>>>> 开始接收DTMF码超时， callId={}, params={}", callId, paramsDtmfStart);
                        }
                    });
        } catch (IOException e) {
            logger.error(">>>>>>>>> 接收呼入码异常", e);
        }
    }
}
