package com.click4bonds.app.Modules.DealConfirmation.Service;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.FileOutputStream;

class TempCouponFormatCheckTest {

    @Test
    void printsCandidateFormats() throws Exception {

        String[] formats = {"0.00\"%\"", "0.00\\%", "0.00\"%\"", "0.00'%'", "0.00\\%"};

        try (Workbook workbook = new XSSFWorkbook()) {

            Sheet sheet = workbook.createSheet("t");
            Row row = sheet.createRow(0);
            DataFormatter formatter = new DataFormatter();

            for (int i = 0; i < formats.length; i++) {

                CellStyle style = workbook.createCellStyle();
                style.setDataFormat(
                        workbook.createDataFormat().getFormat(formats[i]));

                Cell cell = row.createCell(i);
                cell.setCellStyle(style);
                cell.setCellValue(13.70);
            }

            System.out.println("POI version: " + org.apache.poi.Version.getVersion());

            for (int i = 0; i < formats.length; i++) {

                System.out.println("FORMAT [" + formats[i] + "] -> DataFormatter ["
                        + formatter.formatCellValue(row.getCell(i))
                        + "] storedFmt [" + row.getCell(i).getCellStyle()
                                .getDataFormatString() + "]");
            }

            try (FileOutputStream out = new FileOutputStream(
                    "target/coupon-format-candidates.xlsx")) {
                workbook.write(out);
            }
        }
    }
}
