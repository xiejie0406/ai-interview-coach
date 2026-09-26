-- FEAT-FASHION-001 六类平台字典。脚本可重复执行，不创建独立 Fashion 字典表。

insert into sys_dict_type
    (dict_name, dict_type, status, create_by, create_time, update_by, update_time, remark)
select v.dict_name, v.dict_type, '0', 'migration', now(), '', null, 'FEAT-FASHION-001 dictionary'
from (
    select '商品来源' dict_name, 'fashion_product_source' dict_type
    union all select '商品品类', 'fashion_product_category'
    union all select '商品颜色', 'fashion_product_color'
    union all select '商品单位', 'fashion_product_unit'
    union all select '商品季节', 'fashion_product_season'
    union all select '库存仓库', 'fashion_warehouse'
) v
where not exists (select 1 from sys_dict_type t where t.dict_type = v.dict_type);

insert into sys_dict_data
    (dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default,
     status, create_by, create_time, update_by, update_time, remark)
select v.dict_sort, v.dict_label, v.dict_value, v.dict_type, '', '', v.is_default,
       '0', 'migration', now(), '', null, 'FEAT-FASHION-001 initial value'
from (
    select 1 dict_sort, '京东' dict_label, 'JD' dict_value, 'fashion_product_source' dict_type, 'Y' is_default
    union all select 2, '人工维护', 'MANUAL', 'fashion_product_source', 'N'
    union all select 1, '上衣', 'TOP', 'fashion_product_category', 'Y'
    union all select 2, '裤装', 'BOTTOM', 'fashion_product_category', 'N'
    union all select 3, '帽子', 'HAT', 'fashion_product_category', 'N'
    union all select 4, '鞋', 'SHOES', 'fashion_product_category', 'N'
    union all select 1, '黑色', 'BLACK', 'fashion_product_color', 'Y'
    union all select 2, '白色', 'WHITE', 'fashion_product_color', 'N'
    union all select 3, '蓝色', 'BLUE', 'fashion_product_color', 'N'
    union all select 4, '红色', 'RED', 'fashion_product_color', 'N'
    union all select 5, '绿色', 'GREEN', 'fashion_product_color', 'N'
    union all select 1, '件', '件', 'fashion_product_unit', 'Y'
    union all select 2, '双', '双', 'fashion_product_unit', 'N'
    union all select 3, '顶', '顶', 'fashion_product_unit', 'N'
    union all select 1, '四季', '四季', 'fashion_product_season', 'Y'
    union all select 2, '春季', '春季', 'fashion_product_season', 'N'
    union all select 3, '夏季', '夏季', 'fashion_product_season', 'N'
    union all select 4, '秋季', '秋季', 'fashion_product_season', 'N'
    union all select 5, '冬季', '冬季', 'fashion_product_season', 'N'
    union all select 1, '主仓', 'MAIN', 'fashion_warehouse', 'Y'
) v
where exists (select 1 from sys_dict_type t where t.dict_type = v.dict_type)
  and not exists (
      select 1 from sys_dict_data d
       where d.dict_type = v.dict_type and d.dict_value = v.dict_value
  );
