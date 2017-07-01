package cn.snzo.service;

import cn.snzo.vo.ConferenceRoomShow;
import org.springframework.data.domain.Page;

/**
 * Created by Administrator on 2017/7/1 0001.
 */
public interface IConferenceRoomService {

    /**
     * 新增会议室
     * @param conferenceRoomShow
     * @return
     */
    int add(ConferenceRoomShow conferenceRoomShow);

    /**
     * 修改会议室
     * @param id
     * @param conferenceRoomShow
     * @return
     */
    int modify(int id, ConferenceRoomShow conferenceRoomShow);


    /**
     * 删除会议室
     * @param id
     * @return
     */
    int delete(int id);


    /**
     * 分页查会议室
     * @param ivr
     * @param number
     * @param name
     * @param currentPage
     * @param pageSize
     * @return
     */
    Page<ConferenceRoomShow> findPage(String ivr, String number, String name, Integer currentPage, Integer pageSize);
}
