package opensource.onlinestore.service.impl;

import opensource.onlinestore.Utils.Exceptions.NoCategoryException;
import opensource.onlinestore.model.dto.GoodsDTO;
import opensource.onlinestore.model.entity.CategoryEntity;
import opensource.onlinestore.repository.CategoryRepository;
import opensource.onlinestore.service.GoodsService;
import opensource.onlinestore.service.XlsGoodsParser;
import org.apache.commons.io.FileUtils;
import org.apache.poi.hssf.usermodel.HSSFCell;
import org.apache.poi.hssf.usermodel.HSSFRow;
import org.apache.poi.hssf.usermodel.HSSFSheet;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Created by orbot on 04.02.16.
 */
@Service
public class XlsGoodsParserImpl implements XlsGoodsParser {

    private static final Logger LOG = LoggerFactory.getLogger(XlsGoodsParserImpl.class);
    private static final String REGISTRIES_DIR = "goodsregistries/";
    private static final String ARCHIVE_DIR = "archive/";
    private static final String ERRORS_DIR = "errors/";
    private static final String DATE_FORMAT = "dd-MM-yyyy(HH:mm)";
    private DateFormat dateFormat = new SimpleDateFormat(DATE_FORMAT);

    @Autowired
    private CategoryRepository categoryRepository;
    @Autowired
    private GoodsService goodsService;
    @Autowired
    private ErrorGoodsStorageBean errorGoodsStorage;

    @Override
    public void parseGoods() {
        try {
            Resource filesResource = new ClassPathResource(REGISTRIES_DIR);
            File[] files;

            files = filesResource.getFile().listFiles((dir, name) -> {
                return name.endsWith(".xls");
            });

            if(files == null)
                return;

            List<GoodsDTO> goods = new ArrayList<>();
            List<GoodsDTO> parsedGoods;
            for(File file: files) {
                parsedGoods = parseFile(file);
                if(parsedGoods != null) {
                    goods.addAll(parsedGoods);
                }
            }
            try {
                goodsService.addListOfGoods(goods);
            } catch (Exception e) {
                LOG.error("Saving goods error", e);
                return;
            }
            moveFilesToArchive(Arrays.asList(files));
            processErrors();
        } catch (IOException e) {
            LOG.error("Read files error", e);
        }
    }

    private List<GoodsDTO> parseFile(File file) {
        try {
            LOG.info("Starting parsing goods from file {}", file.getName());
            List<GoodsDTO> goodsList = new ArrayList<>();
            FileInputStream fileInputStream = new FileInputStream(file);
            HSSFWorkbook workbook = new HSSFWorkbook(fileInputStream);
            HSSFSheet hssfSheet = workbook.getSheetAt(0);
            Iterator rowIterator = hssfSheet.rowIterator();
            while (rowIterator.hasNext()) {
                HSSFRow row = (HSSFRow)rowIterator.next();
                GoodsDTO parsedGoods = parseRow(row);
                if(parsedGoods != null) {
                    goodsList.add(parsedGoods);
                }
            }
            return goodsList;
        } catch (IOException e) {
            LOG.error("Could not parse file {}", file.getName(), e);
            return null;
        }
    }

    private void moveFilesToArchive(List<File> files) {
        for (File file : files) {
            String absolutePath = file.getAbsolutePath();
            String filePath = absolutePath.substring(0, absolutePath.lastIndexOf(File.separator));
            String newFileName = dateFormat.format(new Date()) + "_archive_" + file.getName();
            String archiveFilePath = filePath + File.separator + ARCHIVE_DIR;
            new File(archiveFilePath).mkdirs();
            File archiveFile = new File(archiveFilePath + newFileName);
            try {
                FileUtils.moveFile(file, archiveFile);
            } catch (IOException e) {
                LOG.info("Moving file error", e);
            }
        }
    }

    private void processErrors() {
        if(errorGoodsStorage.getErrorGoods().isEmpty()) {
            return;
        }
        List<GoodsDTO> errorRows = errorGoodsStorage.getErrorGoods();
        HSSFWorkbook workbook = new HSSFWorkbook();
        HSSFSheet workSheet = workbook.createSheet("Goods error rows");
        HSSFRow row;
        Iterator<HSSFCell> cellIterator;
        HSSFCell errorCell;
        for(int i = 0; i < errorRows.size(); i++) {
            row = workSheet.createRow(i);
            GoodsDTO goods = errorRows.get(i);
            HSSFRow errorRow = goodsToExcelRow(goods, workSheet, i);
            errorRow.createCell(6).setCellValue(goods.getErrorDescription());
            cellIterator = errorRow.cellIterator();
            int cellCounter = 0;
            while(cellIterator.hasNext()) {
                errorCell = cellIterator.next();
                HSSFCell newErrorCell = row.createCell(cellCounter++);
                newErrorCell.setCellValue(errorCell.toString());
            }
        }
        String errorFileName = dateFormat.format(new Date()) + "_errors.xls";
        try {
            File registriesDir = new ClassPathResource(REGISTRIES_DIR).getFile();
            File errorDir = new File(registriesDir.getAbsolutePath() + File.separator + ERRORS_DIR);
            errorDir.mkdirs();
            FileOutputStream fileOut
                    = new FileOutputStream(errorDir.getAbsolutePath() + File.separator + errorFileName);
            workbook.write(fileOut);
            errorGoodsStorage.clearStorage();
        } catch (IOException e) {
            LOG.error("Could not save error file {}", errorFileName, e);
        }
    }

    private GoodsDTO parseRow(HSSFRow row) {
        try {
            GoodsDTO goods = new GoodsDTO();
            goods.setArticle(row.getCell(0).toString());
            goods.setName(row.getCell(1).toString());
            Double countDouble = Double.parseDouble(row.getCell(2).toString());
            goods.setCount(countDouble.longValue());
            goods.setPrice(Double.parseDouble(row.getCell(3).toString()));
            goods.setProducer(row.getCell(4).toString());
            String categoryName = row.getCell(5).toString();
            goods.setCategoryName(categoryName);
            CategoryEntity category = categoryRepository.findByName(categoryName);
            if(category == null) {
                String errorMessage = "No category " + categoryName + " found";
                goods.setErrorDescription(errorMessage);
                errorGoodsStorage.addErrorGoods(goods);
                throw new NoCategoryException(errorMessage);
            }
            return goods;
        } catch (Exception e) {
            LOG.error("Could not parse goods from row", e);
            return null;
        }
    }

    private HSSFRow goodsToExcelRow(GoodsDTO goodsDTO, HSSFSheet workSheet, int rowNum) {
        HSSFRow row = workSheet.createRow(rowNum);
        row.createCell(0).setCellValue(goodsDTO.getArticle());
        row.createCell(1).setCellValue(goodsDTO.getName());
        row.createCell(2).setCellValue(goodsDTO.getCount());
        row.createCell(3).setCellValue(goodsDTO.getPrice());
        row.createCell(4).setCellValue(goodsDTO.getProducer());
        row.createCell(5).setCellValue(goodsDTO.getCategoryName());
        return row;
    }
}
