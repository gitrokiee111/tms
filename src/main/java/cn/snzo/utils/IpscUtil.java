package cn.snzo.utils;

import cn.snzo.common.Constants;
import com.hesong.ipsc.ccf.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Created by chentao on 2017/7/14 0014.
 */
public class IpscUtil {
    private static Logger logger = LoggerFactory.getLogger(IpscUtil.class);

    public static final String VOIP = "10.1.2.152";

    //    private static final String ipscIpAddr = "192.168.2.100"; /// IPSC 服务器的内网地址
    private static final String              ipscIpAddr  = "127.0.0.1"; /// IPSC 服务器的内网地址
    private static final byte                localId     = 24;
    private static final byte                commanderId = 12;
    public static        Commander           commander   = null;
    public static        BusAddress          busAddress  = null;
    public static       Map<String, String> callConfMap = new HashMap<>();



    //    @Override
    public static void init() throws InterruptedException {
        logger.info("Data Bus 客户端单元初始化");
        Unit.initiate(localId, new UnitCallbacks() {
            public void connectSucceed(Client client) {
                logger.info("成功的连接到了IPSC服务程序的 Data Bus");
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

        //创建commander
        logger.info("初始化commander");
        createCommander();
    }


    public static void createCommander() throws InterruptedException {
        /// 新建一个命令发送者
        commander = Unit.createCommander(
                commanderId,
                ipscIpAddr,
                /// 事件监听器
                new RpcEventListener() {
                    public void onEvent(BusAddress busAddress, RpcRequest rpcRequest) {
                        String fullMethodName = rpcRequest.getMethod();
                        if (fullMethodName.startsWith("sys.call")) {
                            /// 呼叫事件
                            String methodName = fullMethodName.substring(9);
                            final String callId = (String) rpcRequest.getParams().get("res_id");
                            if (methodName.equals("on_released")) {
                                logger.warn("呼叫 {} 已经释放", callId);
                            } else if (methodName.equals("on_ringing")) {
                                logger.info("呼叫 {} 振铃", callId);
                            } else if (methodName.equals("on_dial_completed")) {
                                String error = (String) rpcRequest.getParams().get("error");
                                if (error == null) {
                                    String confId = callConfMap.get(callId);
                                    if (confId != null) {
                                        logger.info("呼叫 {} 拨号成功，操作呼叫资源，让它加入会议 {} ...", callId, confId);
                                        addCallToConf(callId, callConfMap.get(callId));
                                    }
                                } else {
                                    logger.error("呼叫 {} 拨号失败：{}", callId, error);
                                }
                            }
                            else if (methodName.equals("on_record_completed")) {
                                String error = (String) rpcRequest.getParams().get("error");
                                logger.info("录音停止callId: {} error: {}", callId, error);
                            }
                        } else if (fullMethodName.startsWith("sys.conf")) {
                            /// 会议事件
                            String methodName = fullMethodName.substring(9);
                            String confId = (String) rpcRequest.getParams().get("res_id");
                            if (methodName.equals("on_released")) {
                                logger.warn("会议 {} 已经释放", confId);
//                                if (confId.equals(conferenceId)) {
//                                    conferenceId = "";
//                                }
                            } else if (methodName.equals("on_record_completed")) {
                                logger.warn("会议 {} 录音已结束", confId);
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
            params.put("max_seconds", 300);
            commander.operateResource(
                    busAddress,
                    callId,
                    "sys.call.conf_enter",
                    params,
                    new RpcResultListener() {
                        @Override
                        protected void onResult(Object o) {
                            logger.info("呼叫 {} 加入会议 {} 操作完毕", callId, conferenceId);
                        }

                        @Override
                        protected void onError(RpcError rpcError) {
                            logger.error("呼叫 {} 加入会议 {} 操作错误: {}", callId, conferenceId, rpcError.getMessage());
                        }

                        @Override
                        protected void onTimeout() {
                            logger.error("呼叫 {} 加入会议 {} 操作超时无响应", callId, conferenceId);
                        }
                    }
            );
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    public static void callOut(String conferenceId, List<String> phones, String ip) throws IOException, InterruptedException {

        phones = phones.stream().map(e -> e+"@"+ip).collect(Collectors.toList());
        for (String te : phones) {
            Map<String, Object> params = new HashMap<String, Object>();
            params.put("to_uri", te); /// 被叫号码的 SIP URI
            params.put("max_answer_seconds", Constants.MAX_ANSWER_SECONDS); /// 该呼叫最长通话允许时间
            IpscUtil.commander.createResource(
                    IpscUtil.busAddress,
                    "sys.call",
                    params,
                    new RpcResultListener() {
                        @Override
                        protected void onResult(Object o) {
                            Map<String, Object> result = (Map<String, Object>) o;
                            String              callId = (String) result.get("res_id");
                            callConfMap.put(callId, conferenceId);
                            logger.info("呼叫资源建立成功，ID={}。系统正在执行外呼……注意这不是呼叫成功！", callId);
                        }

                        @Override
                        protected void onError(RpcError rpcError) {
                            logger.error("创建呼叫资源错误：{} {}", rpcError.getCode(), rpcError.getMessage());
                        }

                        @Override
                        protected void onTimeout() {
                            logger.error("创建呼叫资源超时无响应");
                        }
                    }
            );
        }
    }


    public static void createConference(Map<String, Object> params, RpcResultListener rpcResultListener) throws IOException {
        if (commander != null) {
            commander.createResource(
                    IpscUtil.busAddress,
                    "sys.conf",
                    params,
                    rpcResultListener);
        } else {
            logger.info("commander客户端 未初始化");
        }
    }


    public static void stopConference(String confId, RpcResultListener listener) throws IOException {
        if (commander != null) {
            commander.operateResource(
                    IpscUtil.busAddress,
                    confId,
                    "sys.conf",
                    null,
                    listener);
        } else {
            logger.info("commander客户端 未初始化");
        }
    }


    public static void changePartMode(String confId, String callId, int mode, RpcResultListener listener) throws IOException {
        if (commander != null) {
            Map<String, Object> params = new HashMap<>();
            params.put("res_id", confId);
            params.put("call_res_id", callId);
            params.put("mode", mode);
            logger.info("修改与会者声音收放模式，confid={},callId={},mode={}", confId, callId, mode);
            commander.operateResource(
                    IpscUtil.busAddress,
                    confId,
                    "sys.conf.set_part_voice_mode",
                    params,
                    listener);
        } else {
            logger.info("commander客户端 未初始化");
        }
    }

    public static void exitConferece(String confId, String callId, RpcResultListener listener) throws IOException {
        if (commander != null) {
            Map<String, Object> params = new HashMap<>();
            params.put("res_id", confId);
            params.put("call_res_id", callId);
            logger.info("退出会议，confid={},callId={}", confId, callId);
            commander.operateResource(
                    IpscUtil.busAddress,
                    callId,
                    "sys.call.conf_exit",
                    params,
                    listener);
        } else {
            logger.info("commander客户端 未初始化");
        }
    }

    public static void startRecord(String conferenceId, String path, RpcResultListener listener) throws IOException {
        if (commander != null) {
            Map<String, Object> params = new HashMap<>();
            params.put("res_id", conferenceId);
            params.put("max_seconds", Constants.MAX_CONF_SECONDS);
            params.put("record_file", path);
            params.put("record_format", 3);
            commander.operateResource(
                    IpscUtil.busAddress,
                    conferenceId,
                    "sys.conf.record_start",
                    params,
                    listener);
        } else {
            logger.info("commander客户端 未初始化");
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
            logger.info("commander客户端 未初始化");
        }
    }
}
