package br.com.mesapedido.service;

import br.com.mesapedido.dto.ApiDtos.ProductView;
import br.com.mesapedido.repository.ProductRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MenuService {
  private final ProductRepository products;

  public MenuService(ProductRepository p) {
    products = p;
  }

  @Transactional(readOnly = true)
  public List<ProductView> list() {
    return products.findByAvailableTrueOrderByIdAsc().stream().map(ProductView::of).toList();
  }
}
