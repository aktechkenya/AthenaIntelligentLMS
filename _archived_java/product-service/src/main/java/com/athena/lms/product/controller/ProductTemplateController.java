package com.athena.lms.product.controller;

import com.athena.lms.product.entity.ProductTemplate;
import com.athena.lms.product.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/product-templates")
@RequiredArgsConstructor
public class ProductTemplateController {

    private final ProductService productService;

    @GetMapping
    public List<ProductTemplate> listTemplates() {
        return productService.listTemplates();
    }

    @GetMapping("/{code}")
    public ProductTemplate getTemplate(@PathVariable String code) {
        return productService.getTemplate(code);
    }
}
