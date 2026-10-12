package com.securebank.service;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.regex.Pattern;

public final class InputValidator {

    private static final Pattern USERNAME = Pattern.compile("^[A-Za-z0-9_]{3,20}$");
    private static final Pattern AMOUNT = Pattern.compile("^\\d{1,9}(\\.\\d{1,2})?$");

    private InputValidator() { }

    public static String normalizeUsername(String raw) throws BankException {
        String u = raw == null ? "" : raw.trim();
        if (!USERNAME.matcher(u).matches()) {
            throw new BankException("Username must be 3-20 characters: letters, digits or underscore.");
        }
        return u.toLowerCase(Locale.ROOT);
    }

    public static boolean looksLikeUsername(String raw) {
        return raw != null && USERNAME.matcher(raw.trim()).matches();
    }

    public static void checkPassword(char[] pw) throws BankException {
        String rule = "Password must be 8-64 characters with upper-case, lower-case, a digit and a symbol.";
        if (pw == null || pw.length < 8 || pw.length > 64) throw new BankException(rule);
        boolean up = false, low = false, dig = false, sym = false;
        for (char c : pw) {
            if (Character.isWhitespace(c) || Character.isISOControl(c)) {
                throw new BankException("Password must not contain spaces or control characters.");
            }
            if (Character.isUpperCase(c)) up = true;
            else if (Character.isLowerCase(c)) low = true;
            else if (Character.isDigit(c)) dig = true;
            else sym = true;
        }
        if (!(up && low && dig && sym)) throw new BankException(rule);
    }

    public static BigDecimal parseAmount(String raw, boolean allowZero) throws BankException {
        String s = raw == null ? "" : raw.trim().replace(",", "");
        if (s.isEmpty() && allowZero) return BigDecimal.ZERO.setScale(2);
        if (!AMOUNT.matcher(s).matches()) {
            throw new BankException("Enter a valid amount such as 150 or 150.75 (max 2 decimals).");
        }
        BigDecimal v = new BigDecimal(s).setScale(2);
        if (v.signum() == 0 && !allowZero) throw new BankException("Amount must be greater than zero.");
        return v;
    }
}
