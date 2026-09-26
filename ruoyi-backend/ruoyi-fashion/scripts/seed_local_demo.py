"""只在本机 ry-vue 创建可辨认的智能选品演示数据；不覆盖现有记录。"""

from __future__ import annotations

import hashlib
import json
import os
import shutil
import struct
import subprocess
import zlib
from datetime import datetime, timezone
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]
SOURCE = "DEMO-LOCAL"
BASE = 980_000_000_000
STORAGE = Path("D:/ruoyi/uploadPath/fashion-private/fashion/demo-local")
PRODUCTS = (
    ("top", "TOP", "演示米色上衣", "米色", "BEIGE", "120.00", (193, 158, 118)),
    ("pants", "BOTTOM", "演示深蓝长裤", "深蓝", "NAVY", "100.00", (50, 70, 100)),
    ("hat", "HAT", "演示绿色帽子", "绿色", "GREEN", "30.00", (86, 127, 82)),
    ("shoes", "SHOES", "演示白色鞋子", "白色", "WHITE", "150.00", (210, 210, 205)),
)


def sql_string(value: str) -> str:
    return "'" + value.replace("\\", "\\\\").replace("'", "''") + "'"


def png_chunk(kind: bytes, data: bytes) -> bytes:
    body = kind + data
    return struct.pack(">I", len(data)) + body + struct.pack(">I", zlib.crc32(body))


def shape(kind: str, x: int, y: int) -> bool:
    if kind == "top":
        return (72 <= x <= 184 and 75 <= y <= 210) or (42 <= x <= 72 and 85 <= y <= 145) or (184 <= x <= 214 and 85 <= y <= 145)
    if kind == "pants":
        return (72 <= x <= 184 and 70 <= y <= 105) or (72 <= x <= 119 and 105 <= y <= 218) or (137 <= x <= 184 and 105 <= y <= 218)
    if kind == "hat":
        return ((x - 128) ** 2 + (y - 135) ** 2 <= 70**2 and y <= 145) or (46 <= x <= 210 and 140 <= y <= 158)
    return ((x - 85) ** 2 / 65**2 + (y - 172) ** 2 / 33**2 <= 1) or ((x - 175) ** 2 / 65**2 + (y - 172) ** 2 / 33**2 <= 1)


def demo_png(kind: str, color: tuple[int, int, int]) -> bytes:
    rows = bytearray()
    for y in range(256):
        rows.append(0)
        for x in range(256):
            if shape(kind, x, y):
                pixel = color
            elif 16 <= x <= 239 and 16 <= y <= 239:
                pixel = (238, 235, 229)
            else:
                pixel = (255, 255, 255)
            rows.extend(pixel)
    return (
        b"\x89PNG\r\n\x1a\n"
        + png_chunk(b"IHDR", struct.pack(">IIBBBBB", 256, 256, 8, 2, 0, 0, 0))
        + png_chunk(b"IDAT", zlib.compress(bytes(rows), 9))
        + png_chunk(b"IEND", b"")
    )


def credentials() -> tuple[str, str]:
    values: dict[str, str] = {}
    for line in (ROOT / ".env").read_text(encoding="utf-8").splitlines():
        if "=" in line and not line.startswith("#"):
            key, value = line.split("=", 1)
            values[key] = value.strip().strip('"').strip("'")
    return values["RUOYI_DB_USERNAME"], values["RUOYI_DB_PASSWORD"]


def mysql(sql: str) -> str:
    binary = shutil.which("mysql") or r"C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe"
    user, password = credentials()
    env = {**os.environ, "MYSQL_PWD": password}
    result = subprocess.run(
        [binary, "--host=127.0.0.1", "--port=3306", f"--user={user}",
         "--database=ry-vue", "--default-character-set=utf8mb4", "--batch",
         "--skip-column-names"],
        input=sql.encode("utf-8"), capture_output=True, env=env, check=False,
    )
    if result.returncode:
        raise RuntimeError(result.stderr.decode("utf-8", errors="replace"))
    return result.stdout.decode("utf-8", errors="replace").strip()


def main() -> None:
    counts = mysql(
        "SELECT COUNT(*) FROM fq_product WHERE source_code='DEMO-LOCAL';"
        "SELECT COUNT(*) FROM fq_customer WHERE code='DEMO-LOCAL-CUSTOMER';"
        "SELECT COUNT(*) FROM fq_quote WHERE quote_no='DEMO-LOCAL-001';"
        "SELECT COUNT(*) FROM fq_ai_agent WHERE agent_code LIKE 'demo-local-%';"
        "SELECT COUNT(*) FROM fq_ai_agent_version WHERE id BETWEEN 980000000311 AND 980000000312;"
        "SELECT COUNT(*) FROM fq_import_batch WHERE id BETWEEN 980000000101 AND 980000000103;"
        "SELECT COUNT(*) FROM fq_stock WHERE id BETWEEN 980000000501 AND 980000000504;"
    )
    existing = [int(value) for value in counts.splitlines()]
    if existing == [4, 1, 1, 2, 2, 3, 4]:
        print("demo_data=already_present quote_id=980000000601")
        return
    if any(existing):
        raise RuntimeError(f"演示标识已有部分记录，停止写入：{existing}")
    reserved = mysql(
        "SELECT (SELECT COUNT(*) FROM fq_product WHERE id BETWEEN 980000000401 AND 980000000404)"
        "+(SELECT COUNT(*) FROM fq_customer WHERE id=980000000201)"
        "+(SELECT COUNT(*) FROM fq_quote WHERE id=980000000601)"
        "+(SELECT COUNT(*) FROM fq_ai_agent WHERE id BETWEEN 980000000301 AND 980000000302);"
    )
    if reserved != "0":
        raise RuntimeError("演示数据保留 ID 已被占用，停止写入")

    STORAGE.mkdir(parents=True, exist_ok=True)
    images: list[tuple[str, str]] = []
    for kind, _, _, _, _, _, color in PRODUCTS:
        target = STORAGE / f"{kind}.png"
        content = demo_png(kind, color)
        if target.exists() and target.read_bytes() != content:
            raise RuntimeError(f"演示图片已存在但内容不同：{target}")
        if not target.exists():
            target.write_bytes(content)
        images.append((f"fashion/demo-local/{kind}.png", hashlib.sha256(content).hexdigest()))

    captured = datetime.now(timezone.utc).isoformat(timespec="seconds")
    statements = ["START TRANSACTION;"]
    for dict_type, code, label in (
        ("fashion_product_source", "DEMO-LOCAL", "本机演示"),
        ("fashion_product_color", "BEIGE", "米色"),
        ("fashion_product_color", "NAVY", "深蓝"),
    ):
        statements.append(
            "INSERT INTO sys_dict_data (dict_sort,dict_label,dict_value,dict_type,"
            "css_class,list_class,is_default,status,create_by,create_time,"
            "update_by,update_time,remark) "
            f"SELECT 99,{sql_string(label)},'{code}','{dict_type}','','',"
            "'N','0','demo',NOW(),'',NULL,'本机演示数据' "
            "WHERE NOT EXISTS (SELECT 1 FROM sys_dict_data "
            f"WHERE dict_type='{dict_type}' AND dict_value='{code}');"
        )
    for index, kind in enumerate(("product", "price", "stock"), 1):
        statements.append(
            "INSERT INTO fq_import_batch "
            "(id,batch_no,import_type,operation_type,source_code,warehouse_code,"
            "mapping_snapshot,scope_json,as_of,request_key,expected_count,actual_count,"
            "status,published_at,create_by,create_time,update_by,update_time,row_version) "
            f"VALUES ({BASE + 100 + index},'DEMO-LOCAL-{kind.upper()}',"
            f"'{kind}','import','{SOURCE}','MAIN',JSON_OBJECT(),JSON_OBJECT(),"
            f"NOW(3),'demo-local-{kind}-20260926',4,4,'success',NOW(3),"
            "1,NOW(3),1,NOW(3),1);"
        )
    statements.append(
        "INSERT INTO fq_customer (id,code,name,customer_type,salesperson_id,"
        "collaborator_ids,status,create_by,create_time,update_by,update_time,row_version) "
        "VALUES (980000000201,'DEMO-LOCAL-CUSTOMER','【演示】晨光公司',"
        "'group_purchase',1,JSON_ARRAY(),'active',1,NOW(3),1,NOW(3),1);"
    )
    for index, (kind, category, name, color_name, color_code, price, _) in enumerate(PRODUCTS, 1):
        object_key, digest = images[index - 1]
        image = [{
            "imageId": f"DEMO-{kind.upper()}", "objectKey": object_key,
            "sha256": digest, "usage": "main", "sourceType": "upload",
            "jdId": None, "sourceUrl": None, "capturedAt": captured,
            "allowInternal": True, "allowAi": True, "allowProposal": True,
            "allowEcommerce": False, "status": "active", "confirmedBy": 1,
            "confirmedAt": captured, "width": 256, "height": 256,
            "originalFilename": f"demo-{kind}.png",
        }]
        statements.append(
            "INSERT INTO fq_product (id,source_code,sku_code,style_code,name,"
            "category_code,color_code,color_name,size_code,size_system,unit,sale_price,"
            "currency,tax_mode,price_as_of,last_price_import_batch_id,tags_json,"
            "main_image_key,images_json,visual_version,attributes_confirmed,"
            "attributes_confirmed_by,attributes_confirmed_at,last_product_import_batch_id,"
            "status,create_by,create_time,update_by,update_time,row_version) VALUES ("
            f"{BASE + 400 + index},'{SOURCE}','DEMO-SKU-{kind.upper()}',"
            f"'DEMO-STYLE-{kind.upper()}',{sql_string(name)},'{category}',"
            f"'{color_code}',{sql_string(color_name)},'M','LETTER','件',{price},"
            f"'CNY','included',NOW(3),{BASE + 102},JSON_ARRAY('本机演示'),"
            f"{sql_string(object_key)},{sql_string(json.dumps(image, ensure_ascii=False))},"
            f"1,1,1,NOW(3),{BASE + 101},'active',1,NOW(3),1,NOW(3),1);"
        )
        statements.append(
            "INSERT INTO fq_stock (id,product_id,warehouse_code,available_qty,"
            "as_of,confirmation_type,last_import_batch_id,create_by,create_time,"
            "update_by,update_time,row_version) VALUES ("
            f"{BASE + 500 + index},{BASE + 400 + index},'MAIN',50,NOW(3),"
            f"'import',{BASE + 103},1,NOW(3),1,NOW(3),1);"
        )
    groups = [
        {"count": index, "candidate_count": 1, "slots": ["TOP", "BOTTOM", "HAT", "SHOES"][:index]}
        for index in range(1, 5)
    ]
    statements.append(
        "INSERT INTO fq_quote (id,quote_no,version_no,customer_id,customer_name,"
        "title,salesperson_id,requirement_text,requirement_json,requirement_confirmed,"
        "requested_qty,budget,budget_basis,quote_mode,progressive,combo_template_json,"
        "warehouse_code,currency,tax_mode,presentation_json,status,is_demo,create_by,"
        "create_time,update_by,update_time,row_version) VALUES ("
        "980000000601,'DEMO-LOCAL-001',1,980000000201,'【演示】晨光公司',"
        "'【演示】四品类团建服装方案',1,"
        "'为10人准备上衣、裤子、帽子、鞋的团建服装，偏米色和低饱和配色。',"
        "JSON_OBJECT('preferred_colors',JSON_ARRAY('米色'),'exclusions',JSON_ARRAY()),"
        f"0,10,5000.00,'total','combined',1,{sql_string(json.dumps({'groups': groups}, ensure_ascii=False))},"
        "'MAIN','CNY','included',JSON_OBJECT(),'draft',1,1,NOW(3),1,NOW(3),1);"
    )
    for index, (kind, agent_type) in enumerate((("requirement", "requirement"), ("selection", "selection")), 1):
        agent_id = BASE + 300 + index
        version_id = BASE + 310 + index
        statements.append(
            "INSERT INTO fq_ai_agent (id,agent_code,name,agent_type,description,"
            "current_version_id,status,create_by,create_time,update_by,update_time,"
            "row_version) VALUES ("
            f"{agent_id},'demo-local-{kind}',{sql_string('【本机演示】' + kind)},"
            f"'{agent_type}','本机规则演示，无外部模型调用',NULL,'active',"
            "1,NOW(3),1,NOW(3),1);"
        )
        statements.append(
            "INSERT INTO fq_ai_agent_version (id,agent_id,version_no,provider_code,"
            "model_name,system_instruction,model_config_json,tools_json,handoffs_json,"
            "input_schema_json,output_schema_json,guardrails_json,max_steps,"
            "timeout_seconds,config_hash,status,published_by,published_at,"
            "create_by,create_time,update_by,update_time,row_version) VALUES ("
            f"{version_id},{agent_id},1,'demo','local-rules',"
            "'本机演示结果必须人工确认，不得作为真实模型输出',"
            "JSON_OBJECT(),JSON_ARRAY(),JSON_ARRAY(),JSON_OBJECT(),JSON_OBJECT(),"
            f"JSON_OBJECT(),12,120,SHA2('demo-local-{kind}-v1',256),"
            "'published',1,NOW(3),1,NOW(3),1,NOW(3),1);"
        )
        statements.append(
            f"UPDATE fq_ai_agent SET current_version_id={version_id} WHERE id={agent_id};"
        )
    statements.append("COMMIT;")
    mysql("\n".join(statements))
    print("demo_data=created products=4 customers=1 quotes=1 agents=2 quote_id=980000000601")


if __name__ == "__main__":
    main()
