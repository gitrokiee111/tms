CREATE TABLE IF NOT EXISTS t_history_conference (
  id                 INT(11)          UNSIGNED      AUTO_INCREMENT,
  number             VARCHAR(100)                   COMMENT '编号',
  room_id            INT(11)                        COMMENT '会议室id',
  room_no            VARCHAR(100)                   COMMENT '会议室编号',
  conductor_id       INT(11)                        COMMENT '主持人id',
  conductor_name     VARCHAR(100)                   COMMENT '主持人名字',
  start_at           DATETIME                       COMMENT '操作表id',
  end_at             DATETIME                       COMMENT '操作人',
  create_date        DATETIME                       COMMENT '创建时间',
  modify_date        DATETIME                       COMMENT '修改时间',
  PRIMARY KEY (id),
  UNIQUE KEY (number)
) COMMENT='会议记录表' DEFAULT CHARSET=utf8;
