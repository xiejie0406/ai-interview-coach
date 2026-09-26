package com.ruoyi.aden.application.collection;

import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.ss.usermodel.Sheet;
import tools.jackson.databind.json.JsonMapper;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Function;
import java.util.zip.*;
import static com.ruoyi.aden.application.collection.AdenCollectionService.*;

/** 只读取冻结版本及已存字节，完全不访问源站。所有页面字符串强制使用文本单元格。 */
public final class AdenCollectionExport {
    private AdenCollectionExport() { }
    public static byte[] build(String format,List<Map<String,Object>> selections,Function<String,byte[]> assets) {
        try (XSSFWorkbook workbook=new XSSFWorkbook(); ByteArrayOutputStream xlsx=new ByteArrayOutputStream()) {
            Sheet products=workbook.createSheet("商品汇总"), images=workbook.createSheet("图片清单"), snapshots=workbook.createSheet("快照说明"), errors=workbook.createSheet("异常清单");
            row(products,List.of("内部商品ID","快照ID","来源","SKU","原始标题","人工标题","原始价格","人工价格","价格原文","店铺","来源链接","备注","标签","状态","整理版本","原始价格精确文本","人工价格精确文本"));
            row(images,List.of("商品ID","快照ID","图片ID","分组","状态","已移除","来源","文件路径","错误","整理顺序","主图"));
            row(snapshots,List.of("商品ID","快照ID","采集时间","图片清单版本","完整性","内容区块"));
            row(errors,List.of("商品ID","图片ID","原因"));
            var zipFiles=new LinkedHashMap<String,byte[]>();
            for(var selection:selections) {
                var snapshot=object(selection.get("snapshot")); var fields=object(snapshot.get("fields")); var curation=object(selection.get("curation"));
                // 不将其他快照的人工图片整理套用到历史版本。
                boolean same=Objects.equals(curation.get("snapshotId"),snapshot.get("snapshotId"));
                var overrides=same?object(curation.get("overrides")):Map.<String,Object>of();
                String id=str(selection.get("itemId")), sid=str(snapshot.get("snapshotId"));
                row(products,Arrays.asList(id,sid,snapshot.get("source"),fields.get("sku"),fields.get("title"),overrides.get("title"),fields.get("price"),overrides.get("price"),fields.get("priceText"),fields.get("shop"),fields.get("sourceUrl"),curation.get("notes"),curation.get("tags"),snapshot.get("status"),curation.get("revision"),fields.get("price"),overrides.get("price")));
                money(products,6,fields.get("price")); money(products,7,overrides.get("price"));
                row(snapshots,Arrays.asList(id,sid,snapshot.get("createdAt"),snapshot.get("assetManifestVersion"),snapshot.get("completeness"),snapshot.get("blocks")));
                Set<Object> removed=same?new HashSet<>(listValue(curation.get("removedImageIds"))):Set.of();
                var arranged=objects(snapshot.get("images")); var order=same?listValue(curation.get("imageOrder")):List.of();
                arranged.sort(Comparator.comparingInt(i->{int position=order.indexOf(i.get("imageId"));return position<0?order.size()+(int)number(i.get("order")):position;}));
                int position=0;
                for(var image:arranged) {
                    boolean excluded=removed.contains(image.get("imageId")); boolean saved="SAVED".equals(image.get("status"));
                    String assetId=str(image.get("assetId"));
                    String extension=switch(str(image.get("mimeType"))) { case "image/png"->".png"; case "image/webp"->".webp"; default->".jpg"; };
                    String path=saved&&!excluded&&format.equals("ZIP")?"图片/"+id+"/"+sid+"/"+assetId+extension:"";
                    row(images,Arrays.asList(id,sid,image.get("imageId"),image.get("group"),image.get("status"),excluded?"是":"否",image.get("source"),path,image.get("error"),++position,same&&Objects.equals(curation.get("mainImageId"),image.get("imageId"))?"是":"否"));
                    if(!saved || excluded) row(errors,List.of(id,str(image.get("imageId")),excluded?"用户已移除":str(image.getOrDefault("error","尚未保存"))));
                    if(!path.isEmpty()) zipFiles.put(path,assets.apply(assetId));
                }
                if(format.equals("ZIP")) zipFiles.put("内容快照/"+id+"/"+sid+".json",JsonMapper.builder().build().writeValueAsBytes(selection));
            }
            for(Sheet sheet:List.of(products,images,snapshots,errors)) { sheet.createFreezePane(0,1); for(int c=0;c<sheet.getRow(0).getLastCellNum();c++)sheet.setColumnWidth(c,24*256); }
            workbook.write(xlsx);
            if(format.equals("XLSX"))return xlsx.toByteArray();
            try(ByteArrayOutputStream output=new ByteArrayOutputStream(); ZipOutputStream zip=new ZipOutputStream(output,StandardCharsets.UTF_8)) {
                zipFiles.put("商品采集.xlsx",xlsx.toByteArray());
                zipFiles.put("导出说明.txt","本包为创建导出时固定的商品、快照、人工修订与图片清单版本。未加载或保存失败的内容不会补抓；图片缺失及移除项见Excel异常清单。\n".getBytes(StandardCharsets.UTF_8));
                for(var entry:zipFiles.entrySet()) { zip.putNextEntry(new ZipEntry(entry.getKey())); zip.write(entry.getValue()); zip.closeEntry(); }
                zip.finish(); return output.toByteArray();
            }
        } catch(IOException e) { throw new IllegalStateException("导出生成失败",e); }
    }
    private static void row(Sheet sheet,List<?> values) {
        var row=sheet.createRow(sheet.getPhysicalNumberOfRows());
        for(int i=0;i<values.size();i++) {
            String text=str(values.get(i)); if(text.length()>32767)text=text.substring(0,32740)+"…（内容见JSON快照）";
            row.createCell(i,org.apache.poi.ss.usermodel.CellType.STRING).setCellValue(text);
        }
    }
    private static void money(Sheet sheet,int column,Object value) {
        if(value==null || str(value).isBlank())return;
        validatePrice(value);
        sheet.getRow(sheet.getLastRowNum()).getCell(column).setCellValue(new java.math.BigDecimal(str(value)).doubleValue());
    }
}
