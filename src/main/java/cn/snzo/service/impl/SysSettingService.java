package cn.snzo.service.impl;

import cn.snzo.entity.Account;
import cn.snzo.entity.SysSetting;
import cn.snzo.repository.AccountRepository;
import cn.snzo.repository.SysSettingRepository;
import cn.snzo.service.ISysSettingService;
import cn.snzo.utils.BeanUtil;
import cn.snzo.utils.Md5Utils;
import cn.snzo.vo.AccountShow;
import cn.snzo.vo.SysSettingShow;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * Created by Administrator on 2017/7/4 0004.
 */
@Service
public class SysSettingService implements ISysSettingService {

    @Autowired
    private SysSettingRepository sysSettingRepository;

    @Autowired
    private AccountRepository    accountRepository;

    @Override
    public int add(SysSettingShow sysSettingShow) {
        SysSetting sysSetting = new SysSetting();
        BeanUtil.showToEntity(sysSettingShow, sysSetting);
        sysSettingRepository.save(sysSetting);
        return 0;
    }


    @Override
    public int modify(int id, SysSettingShow sysSettingShow) {
        SysSetting check = sysSettingRepository.findOne(id);
        if (check == null)
            return 2;
        SysSetting sysSetting = new SysSetting();
        BeanUtil.showToEntity(sysSettingShow, sysSetting);
        sysSettingRepository.save(sysSetting);
        return 1;
    }

    @Override
    public int modifyPwd(AccountShow accountShow) {
        String username = accountShow.getUsername();
        Account check = accountRepository.findByUsername(username);
        if (check == null) {
           return 2;
        }
        if (StringUtils.isEmpty(accountShow.getNewPassword())) {
            return 3;
        }
        String password = accountShow.getPassword();
        String md5Pwd = Md5Utils.MD5(password+ check.getSalt());
        System.out.println(Md5Utils.MD5("admin123"));
        System.out.println(Md5Utils.MD5("admin123"));
        if (md5Pwd == null) {
            return 4;
        }
        if (!md5Pwd.equals(check.getPassword())) {
            return 5;
        }
        check.setPassword(Md5Utils.MD5(accountShow.getNewPassword()+check.getSalt()));
        accountRepository.save(check);
        return 1;
    }

    @Override
    public SysSettingShow getLatestSetting() {
        SysSetting sysSetting = sysSettingRepository.findFirstByOrderByIdDesc();
        if (sysSetting != null) {
            SysSettingShow sysSettingShow = new SysSettingShow();
            BeanUtil.entityToShow(sysSetting, sysSettingShow);
            return sysSettingShow;
        }
        return null;
    }
}
