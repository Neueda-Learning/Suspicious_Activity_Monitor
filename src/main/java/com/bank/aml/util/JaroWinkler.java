package com.bank.aml.util;

public final class JaroWinkler {
    private JaroWinkler() {}

    public static double similarity(String s1, String s2) {
        if (s1 == null || s2 == null) return 0;
        String a = s1.trim().toLowerCase();
        String b = s2.trim().toLowerCase();
        if (a.isEmpty() || b.isEmpty()) return 0;
        if (a.equals(b)) return 1.0;
        int[] mtp = matches(a, b);
        double m = mtp[0];
        if (m == 0) return 0;
        double j = ((m / a.length()) + (m / b.length()) + ((m - mtp[1]) / m)) / 3.0;
        double jw = j;
        if (j > 0.7) {
            int prefix = Math.min(mtp[2], 4);
            jw = j + prefix * 0.1 * (1 - j);
        }
        return Math.min(1.0, jw);
    }

    private static int[] matches(String s1, String s2) {
        String max = s1.length() > s2.length() ? s1 : s2;
        String min = s1.length() > s2.length() ? s2 : s1;
        int range = Math.max(max.length() / 2 - 1, 0);
        boolean[] matchFlags = new boolean[max.length()];
        boolean[] minMatchFlags = new boolean[min.length()];
        int matches = 0;
        for (int i = 0; i < min.length(); i++) {
            int start = Math.max(0, i - range);
            int end = Math.min(i + range + 1, max.length());
            for (int j = start; j < end; j++) {
                if (matchFlags[j] || min.charAt(i) != max.charAt(j)) continue;
                matchFlags[j] = true;
                minMatchFlags[i] = true;
                matches++;
                break;
            }
        }
        char[] ms1 = new char[matches];
        char[] ms2 = new char[matches];
        int si = 0;
        for (int i = 0; i < min.length(); i++) if (minMatchFlags[i]) ms1[si++] = min.charAt(i);
        si = 0;
        for (int i = 0; i < max.length(); i++) if (matchFlags[i]) ms2[si++] = max.charAt(i);
        int transpositions = 0;
        for (int i = 0; i < matches; i++) if (ms1[i] != ms2[i]) transpositions++;
        int prefix = 0;
        for (int i = 0; i < Math.min(4, min.length()); i++) {
            if (s1.charAt(i) == s2.charAt(i)) prefix++;
            else break;
        }
        return new int[]{matches, transpositions / 2, prefix};
    }
}
