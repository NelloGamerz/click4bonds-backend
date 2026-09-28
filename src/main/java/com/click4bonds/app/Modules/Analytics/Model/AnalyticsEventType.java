package com.click4bonds.app.Modules.Analytics.Model;

//public enum AnalyticsEventType {
//
//    LOGIN,
//    LOGOUT,
//
//    BOND_VIEW,
//    BOND_CLICK,
//
//    RFQ_VIEW,
//    RFQ_CLICK,
//    RFQ_SUBMIT,
//
//    ORDER_VIEW,
//    ORDER_SUBMIT,
//
//    SEARCH
//}


public enum AnalyticsEventType {

    // Authentication
    LOGIN,
    LOGOUT,
    LOGIN_FAILED,
    SIGNUP,
    PASSWORD_RESET,
    SESSION_EXPIRED,

    // User/Profile
    PROFILE_VIEW,
    PROFILE_UPDATE,
    PROFILE_COMPLETE,

    // Bonds
    BOND_VIEW,
    BOND_CLICK,
    BOND_SEARCH,
    BOND_FILTER,
    //    BOND_SORT,
    BOND_COMPARE,
    //    BOND_WATCHLIST_ADD,
//    BOND_WATCHLIST_REMOVE,
    BOND_DETAILS_VIEW,
//    BOND_DOCUMENT_VIEW,
//    BOND_DOWNLOAD,

//    // RFQ
//    RFQ_VIEW,
//    RFQ_CLICK,
//    RFQ_CREATE,
    RFQ_SUBMIT,
//    RFQ_CANCEL,
//    RFQ_EDIT,
//    RFQ_ACCEPT,
//    RFQ_REJECT,
//    RFQ_EXPIRE,
//    RFQ_RESPONSE_VIEW,

    // Orders
//    ORDER_VIEW,
    ORDER_CREATE,
    ORDER_SUBMIT,
    //    ORDER_EDIT,
//    ORDER_CANCEL,
    ORDER_CONFIRM,
    //    ORDER_REJECT,
    ORDER_COMPLETED,

    // Search
    SEARCH,
    //    SEARCH_SUBMIT,
//    SEARCH_RESULT_CLICK,
    SEARCH_FILTER,
    //    SEARCH_SORT,
    SEARCH_NO_RESULT,

    // Portfolio
//    PORTFOLIO_VIEW,
    PORTFOLIO_HOLDING_VIEW,
    PORTFOLIO_TRANSACTION_VIEW,
//    PORTFOLIO_PERFORMANCE_VIEW,

    // Watchlist
//    WATCHLIST_VIEW,
    WATCHLIST_ADD,
    WATCHLIST_REMOVE,

    // Notifications
//    NOTIFICATION_VIEW,
//    NOTIFICATION_CLICK,
//    NOTIFICATION_DISMISS,

    // Documents
    DOCUMENT_VIEW,
//    DOCUMENT_DOWNLOAD,
//    DOCUMENT_UPLOAD,

    // Payments / Settlement
    PAYMENT_INITIATED,
    PAYMENT_COMPLETED,
    PAYMENT_FAILED,
    SETTLEMENT_INITIATED,
    SETTLEMENT_COMPLETED,
    SETTLEMENT_FAILED,

    // App / Navigation
    PAGE_VIEW,
    //    SCREEN_VIEW,
//    MENU_CLICK,
    BUTTON_CLICK,
    LINK_CLICK,

    // Errors
//    ERROR,
//    API_ERROR,
//    VALIDATION_ERROR,

    // General
    SESSION_START,
    SESSION_END
}
