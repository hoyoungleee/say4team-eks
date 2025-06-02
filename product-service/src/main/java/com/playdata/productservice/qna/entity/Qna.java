package com.playdata.productservice.qna.entity;

import com.playdata.productservice.common.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Getter@Setter@ToString
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Entity
@Table(name = "tbl_QnA")
public class Qna extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private String content;

    @Column(nullable = true)
    private String uccUrl;

    @Column(nullable = false)
    private String qnaPassword;

    @Column(nullable = false)
    private String qnaSecretYn;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    private Integer viewCount;

    private String delYn;

}
