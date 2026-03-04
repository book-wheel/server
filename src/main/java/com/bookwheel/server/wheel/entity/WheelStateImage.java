package com.bookwheel.server.wheel.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter
@Builder
@Table(name = "wheel_state_image")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PROTECTED)
public class WheelStateImage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "image_id")
    private Long imageId;

    @Column(name = "image_url", nullable = false, length = 255)
    private String imageUrl;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "wheel_state_id", nullable = false)
    private WheelState wheelState;

    public static WheelStateImage of(String imageUrl, WheelState wheelState) {
        return WheelStateImage.builder()
                .imageUrl(imageUrl)
                .wheelState(wheelState)
                .build();
    }

}