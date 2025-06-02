package com.playdata.productservice.qna.dto;

import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class QnaResDto {
    private String title;

    private String content;

    private String name;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    private Integer viewCount;

}
