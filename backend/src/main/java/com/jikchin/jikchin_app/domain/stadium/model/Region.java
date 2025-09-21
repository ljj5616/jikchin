package com.jikchin.jikchin_app.domain.stadium.model;

public enum Region {
    // KBO 제1 홈구장
    JAMSIL("잠실"), SAJIK("사직"), GOCHEOK("고척"), MUNHAK("문학"), DAEJEON("대전"),
    GWANGJU("광주"), DAEGU("대구"), SUWON("수원"), CHANGWON("창원"),

    // K리그 홈구장
    ULSAN("울산"), CHUNCHEON("춘천"), JEJU("제주"), JEONJU("전주"), SEOUL("서울"),
    KIMCHEON("김천"), POHANG("포항"), GANGNEUNG("강릉"), ANYANG("안양"),

    // KBO 제2 홈구장
    CHEONGJU("청주"), GUNSAN("군산");

    private final String koLabel;
    Region(String koLabel) { this.koLabel = koLabel; }
    public String ko() { return koLabel; }
}
