package cn.snzo.service.impl;

import cn.snzo.common.CommonUtils;
import cn.snzo.entity.Recording;
import cn.snzo.repository.RecordingRepository;
import cn.snzo.service.IRecordingService;
import cn.snzo.utils.PageUtil;
import cn.snzo.vo.RecordingShow;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.Date;


/**
 * Created by Administrator on 2017/7/4 0004.
 */
@Service
public class RecordingService implements IRecordingService {

    @Autowired
    private RecordingRepository recordingRepository;

    @Override
    public int delete(int rid) {
        recordingRepository.delete(rid);
        return 1;
    }

    @Override
    public Page<RecordingShow> getPage(String filename, Date createStart, Date createEnd,
                                       Integer currentPage, Integer pageSize) {
        Pageable p = PageUtil.createPage(currentPage, pageSize);
        filename = CommonUtils.fuzzyString(filename);
        Page<Recording> recordings = recordingRepository.findPage(filename, createStart, createEnd, p);
        return PageUtil.entityToShowPage(recordings, p);
    }
}
