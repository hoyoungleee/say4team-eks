package com.playdata.productservice.product.entity;

import com.playdata.productservice.common.entity.BaseTimeEntity;
import com.playdata.productservice.product.dto.ProductResDto;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Getter @Setter @ToString
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "tbl_product")
public class Product extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "product_id" , nullable = false)
    private Long productId;

    private String name;
    private int price;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", nullable = false) // 외래키 컬럼명 지정
    private Category category;


    private int stockQuantity;

    @Column(length = 1000)
    private String mainImagePath;
    @Column(length = 1000)
    private String thumbnailPath;

    private String description;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ProductImages> productImages;


    public ProductResDto fromEntity() {
        return ProductResDto.builder()
                .id(productId)
                .name(name)
                .categoryId(category.getCategoryId())
                .categoryName(category.getName())
                .price(price)
                .description(description)
                .stockQuantity(stockQuantity)
                .mainImagePath(mainImagePath)
                .thumbnailPath(thumbnailPath)
                .createTime(createTime)
                .updateTime(updateTime)
                .productImages(
                    this.productImages.stream()
                            .map(ProductImages::getImgUrl)
                            .collect(Collectors.toList())
                )
                .build();
    }

}







