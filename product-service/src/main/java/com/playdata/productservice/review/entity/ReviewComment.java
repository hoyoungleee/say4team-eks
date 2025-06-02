package com.playdata.productservice.review.entity;

import jakarta.persistence.*;
import lombok.*;

@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "tbl_reviewComment")
public class ReviewComment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(nullable = false, name="comment_Id")
    private String commentId;


    @Column(name = "user_email", nullable = false)
    private String userEmail;

    @Column(name="user_name", nullable = false)
    private String userName;

    @Column(name = "comment_pw", nullable = false)
    private String commentPw;

    @Column(name = "comment_content", nullable = false)
    private String commentContent;
}
