package com.example.viewmodel

/**
 * Exact 50 physical keys of the German CASIO fx-991DE X ClassWiz.
 */
enum class KeyId {
    // Control & Navigation Cluster
    SHIFT, ALPHA, UP, DOWN, LEFT, RIGHT, MENU_SETUP, ON,

    // Row 1 (Scientific)
    OPTN, CALC, INTEGRAL, X_VAR,

    // Row 2 (Scientific)
    FRAC, SQRT, X_SQ, X_POW, LOG_BASE, LN,

    // Row 3 (Scientific)
    NEGATE, DMS, RECIPROCAL, SIN, COS, TAN,

    // Row 4 (Scientific)
    STO, ENG, LPAREN, RPAREN, SD, M_PLUS,

    // Row 5 (Numpad)
    NUM_7, NUM_8, NUM_9, DEL, AC,

    // Row 6 (Numpad)
    NUM_4, NUM_5, NUM_6, MUL, DIV,

    // Row 7 (Numpad)
    NUM_1, NUM_2, NUM_3, PLUS, MINUS,

    // Row 8 (Numpad)
    NUM_0, DECIMAL, EXP10, ANS, EQUALS
}
