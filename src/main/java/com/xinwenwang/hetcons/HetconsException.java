package com.xinwenwang.hetcons;

public class HetconsException extends Exception {

    private HetconsErrorCode errorCode;
    private String info;


    public HetconsException(HetconsErrorCode code) {
        this(code, code.toString());
    }

    public HetconsException(HetconsErrorCode errorCode, String info) {
        this.errorCode = errorCode;
        this.info = info;
    }

}

enum HetconsErrorCode {
    NO_1A_MESSAGES,
    NO_1B_MESSAGES,
    NO_2B_MESSAGES,
    EMPTY_MESSAGE,
    NO_PROPOSAL
}
