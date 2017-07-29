package cn.snzo.ipsc;

import cn.snzo.entity.Call;
import cn.snzo.entity.Log;
import cn.snzo.repository.CallRepository;
import cn.snzo.repository.LogRepository;

/**
 * Created by Administrator on 2017/7/29 0029.
 */
public class CreateCallRpcResultListener extends SimpleRpcResultListener {

    private Call call;
    private CallRepository callRepository;

    public CreateCallRpcResultListener(Log log, LogRepository logRepository, String rpcMethodName, Call call, CallRepository callRepository) {
        super(log, logRepository, rpcMethodName);
        this.call = call;
        this.callRepository = callRepository;
    }

    public CreateCallRpcResultListener(Log log, LogRepository logRepository, String rpcMethodName) {
        super(log, logRepository, rpcMethodName);
    }

    protected void additionExcute(){
        logger.info("===========> excute additionExcute()");
        String callId = (String) getRpcResult().get("res_id");
        call.setResId(callId);
        callRepository.save(call);
    }
}
