package com.zhikao.common;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Excel 读写小工具（基于 Apache POI），供批量导入与模板下载使用。
 * 约定：只处理第一个 Sheet，所有单元格统一按字符串读取。
 */
public final class ExcelHelper {

    private ExcelHelper() {
    }

    /**
     * 读取 Excel（.xls / .xlsx）为字符串矩阵，第一行为表头。
     * 以表头行的列数为准补齐空串，保证每行列数一致、便于按下标取值。
     *
     * @param file 上传的文件
     * @return 每行一个 List，第 0 行为表头
     */
    public static List<List<String>> read(MultipartFile file) {
        List<List<String>> rows = new ArrayList<>();
        try (InputStream in = file.getInputStream();
             Workbook workbook = WorkbookFactory.create(in)) {
            Sheet sheet = workbook.getSheetAt(0);
            Row header = sheet.getRow(sheet.getFirstRowNum());
            int columns = header == null ? 0 : header.getLastCellNum();
            if (columns <= 0) {
                throw new BusinessException("Excel 首个 Sheet 没有表头，请使用导入模板填写");
            }
            for (int r = sheet.getFirstRowNum(); r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                List<String> line = new ArrayList<>(columns);
                for (int c = 0; c < columns; c++) {
                    line.add(row == null ? "" : cellText(row.getCell(c)));
                }
                rows.add(line);
            }
        } catch (IOException e) {
            throw new BusinessException("Excel 读取失败：" + e.getMessage());
        }
        return rows;
    }

    /**
     * 生成单 Sheet 的 xlsx 字节：第 1 行表头，之后为数据行。
     * 导入模板与数据导出共用同一实现。
     *
     * @param sheetName Sheet 名称
     * @param headers   表头
     * @param rows      数据行（导出时传真实数据；模板时传示例行，可为空列表）
     */
    public static byte[] write(String sheetName, List<String> headers, List<List<String>> rows) {
        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet(sheetName);

            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.size(); i++) {
                headerRow.createCell(i).setCellValue(headers.get(i));
                sheet.setColumnWidth(i, 20 * 256); // 20 字符宽，避免打开后列过窄
            }

            for (int r = 0; r < rows.size(); r++) {
                Row row = sheet.createRow(r + 1);
                List<String> line = rows.get(r);
                for (int c = 0; c < line.size(); c++) {
                    row.createCell(c).setCellValue(line.get(c));
                }
            }

            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new BusinessException("Excel 生成失败：" + e.getMessage());
        }
    }

    /**
     * 单元格转字符串。
     * 数值型整数去掉小数尾巴（1.0 -> 1），避免手机号、账号被读成 1.38E10 这类科学计数法。
     */
    private static String cellText(Cell cell) {
        if (cell == null) {
            return "";
        }
        CellType type = cell.getCellType();
        if (type == CellType.NUMERIC) {
            double value = cell.getNumericCellValue();
            return value == Math.floor(value) ? String.valueOf((long) value) : String.valueOf(value);
        }
        if (type == CellType.BOOLEAN) {
            return String.valueOf(cell.getBooleanCellValue());
        }
        if (type == CellType.FORMULA) {
            return cell.getCellFormula();
        }
        return cell.getStringCellValue().trim();
    }
}
