package com.rushcrew.product.infrastructure.repostiory;

import com.rushcrew.product.application.ProductFilter;
import com.rushcrew.product.application.result.ProductResult;
import com.rushcrew.product.domain.entity.Product;
import com.rushcrew.product.domain.entity.ProductOption;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ProductJpaRepository extends JpaRepository<Product, UUID> {

    Optional<Product> findByIdAndDeletedAtIsNull(UUID productId);
           
           
    @Query(value = """
                SELECT new com.rushcrew.product.application.result.ProductResult(
                          p.id,
                          p.companyName,
                          p.productInfo.name,
                          p.productInfo.description,
                          p.price.amount,
                          p.imageUrl
                     )
                FROM Product p
                WHERE p.isActive = true
                  AND (:#{#filter.category} IS NULL OR p.category IN :#{#filter.category})
                  AND (:#{#filter.minPrice} IS NULL OR p.price.amount >= :#{#filter.minPrice})
                  AND (:#{#filter.maxPrice} IS NULL OR p.price.amount <= :#{#filter.maxPrice})
                  AND p.deletedAt IS NULL
        """)
    Page<ProductResult> searchEnabledProducts(
        @Param("filter") ProductFilter productFilter,
        Pageable pageable
    );


    @Query("""
                    SELECT DISTINCT p
                    FROM Product p
                    LEFT JOIN FETCH p.options
                    WHERE p.id = :productId
                      AND p.isActive = true
                      AND p.deletedAt IS NULL
        """)
    Optional<Product> findProductDetail(UUID productId);
           
           

    @Query("""
                    SELECT po
                    FROM ProductOption po
                    WHERE po.id = :skuId
                      AND po.deletedAt IS NULL
        """)
    Optional<ProductOption> findOptionBySkuId(UUID skuId);
}
