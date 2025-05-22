package com.example.demo.dto;

import lombok.Data;


public class MatchingPairDTO {
    private String leftItem;
    private String rightItem;
    private String leftItemType = "TEXT";
    private String rightItemType = "TEXT";
    private String leftItemUrl;
    private String rightItemUrl;
    private Integer points;

    public String getLeftItem() {
        return leftItem;
    }

    public void setLeftItem(String leftItem) {
        this.leftItem = leftItem;
    }

    public String getRightItem() {
        return rightItem;
    }

    public void setRightItem(String rightItem) {
        this.rightItem = rightItem;
    }

    public String getLeftItemType() {
        return leftItemType;
    }

    public void setLeftItemType(String leftItemType) {
        this.leftItemType = leftItemType;
    }

    public String getRightItemType() {
        return rightItemType;
    }

    public void setRightItemType(String rightItemType) {
        this.rightItemType = rightItemType;
    }

    public String getLeftItemUrl() {
        return leftItemUrl;
    }

    public void setLeftItemUrl(String leftItemUrl) {
        this.leftItemUrl = leftItemUrl;
    }

    public String getRightItemUrl() {
        return rightItemUrl;
    }

    public void setRightItemUrl(String rightItemUrl) {
        this.rightItemUrl = rightItemUrl;
    }

    public Integer getPoints() {
        return points;
    }

    public void setPoints(Integer points) {
        this.points = points;
    }
}