-- FEAT-FASHION-001 非敏感类型化配置初始值。Secret、人员范围、Agent 版本不得写入 sys_config。

insert into sys_config
    (config_name, config_key, config_value, config_type, create_by, create_time,
     update_by, update_time, remark)
select v.config_name, v.config_key, v.config_value, 'N', 'migration', now(), '', null,
       'Fashion typed setting; update through /fashion/settings only'
from (
    select '库存新鲜度（小时）' config_name, 'fashion.stock.freshnessHours' config_key, '24' config_value
    union all select '报价默认有效期（天）', 'fashion.quote.defaultValidDays', '7'
    union all select '报价默认优惠率（%）', 'fashion.quote.defaultDiscountPercent', '5.00'
    union all select '单任务图片结果上限', 'fashion.image.maxResults', '2'
    union all select 'AI 消息保留天数', 'fashion.retention.messageDays', '365'
    union all select '报价与交付保留天数', 'fashion.retention.quoteDays', '365'
    union all select '原始导入文件保留天数', 'fashion.retention.importFileDays', '90'
    union all select '失败素材保留天数', 'fashion.retention.failedImageDays', '30'
    union all select 'AI Run 卡住告警阈值（分钟）', 'fashion.alert.runStuckMinutes', '15'
    union all select '批次失败率阈值（%）', 'fashion.alert.importFailurePercent', '10.00'
    union all select '图片失败待查阈值（个）', 'fashion.alert.imagePendingCount', '10'
    union all select '库存过期数量阈值（个）', 'fashion.alert.stockExpiredCount', '50'
    union all select '文件失败率阈值（%）', 'fashion.alert.deliveryFailurePercent', '10.00'
    union all select 'AI 月度预算（CNY）', 'fashion.ai.monthlyBudgetCny', '0.00'
    union all select '默认核验仓库', 'fashion.catalog.defaultWarehouse', 'MAIN'
) v
where not exists (select 1 from sys_config c where c.config_key = v.config_key);
