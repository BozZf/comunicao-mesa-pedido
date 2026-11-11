package br.com.mesapedido.service;

import br.com.mesapedido.domain.DiningTable;
import br.com.mesapedido.exception.BusinessException;
import br.com.mesapedido.repository.TableRepository;
import com.google.zxing.*;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.qrcode.QRCodeWriter;
import java.io.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TableService {
  private final TableRepository tables;
  private final String baseUrl;

  public TableService(TableRepository t, @Value("${app.base-url}") String base) {
    tables = t;
    baseUrl = base;
  }

  @Transactional
  public List<Map<String, Object>> list() {
    return tables.findAll().stream()
        .map(
            t ->
                Map.<String, Object>of(
                    "number",
                    t.id,
                    "active",
                    t.active,
                    "url",
                    baseUrl + "/?mesa=" + t.token,
                    "qr",
                    "/api/cashier/tables/" + t.id + "/qr"))
        .toList();
  }

  @Transactional
  public DiningTable create(int number) {
    if (number < 1 || number > 999)
      throw new BusinessException(HttpStatus.BAD_REQUEST, "Mesa deve estar entre 1 e 999.");
    if (tables.existsById(number))
      throw new BusinessException(HttpStatus.CONFLICT, "Mesa já existe.");
    var t = new DiningTable();
    t.id = number;
    t.active = true;
    t.token = UUID.randomUUID().toString();
    return tables.save(t);
  }

  @Transactional(readOnly = true)
  public byte[] qr(int number) throws Exception {
    var t =
        tables
            .findById(number)
            .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Mesa não encontrada."));
    var matrix =
        new QRCodeWriter().encode(baseUrl + "/?mesa=" + t.token, BarcodeFormat.QR_CODE, 350, 350);
    var out = new ByteArrayOutputStream();
    MatrixToImageWriter.writeToStream(matrix, "PNG", out);
    return out.toByteArray();
  }
}
