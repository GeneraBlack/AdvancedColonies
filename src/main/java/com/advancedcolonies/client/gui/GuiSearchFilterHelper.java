package com.advancedcolonies.client.gui;

import com.advancedcolonies.config.AdvancedColoniesConfig;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.util.Locale;

/**
 * Client-side helper for enhanced item search filtering across MineColonies and Structurize
 * GUI windows ({@code ItemListModuleWindow}, {@code RestaurantMenuModuleWindow},
 * {@code WindowHutAllInventory}, {@code WindowPostBoxMain}, and {@code WindowSelectRes}):
 * <ul>
 *   <li>Supports multi-keyword search (e.g. {@code "oak door"} matches {@code "Dark Oak Door"}).</li>
 *   <li>Automatically integrates with <i>JustEnoughCharacters</i> (JEC) Pinyin matching when installed.</li>
 * </ul>
 */
public final class GuiSearchFilterHelper {

    private static boolean jecChecked = false;
    @Nullable
    private static Method jecMatchContains = null;

    private GuiSearchFilterHelper() {
    }

    /**
     * Evaluates whether {@code candidateText} matches {@code filterSeq}.
     *
     * @param candidateText the lowercased item display name or identifier string
     * @param filterSeq     the user's search query
     * @return {@code true} if the candidate matches the search query
     */
    public static boolean matches(@Nullable final String candidateText, @Nullable final CharSequence filterSeq) {
        if (candidateText == null) {
            return false;
        }
        if (filterSeq == null || filterSeq.isEmpty()) {
            return true;
        }

        if (!AdvancedColoniesConfig.CITIZEN_COMMON.enableEnhancedGuiItemSearch.get()) {
            return candidateText.contains(filterSeq);
        }

        final String query = filterSeq.toString().trim().toLowerCase(Locale.ROOT);
        if (query.isEmpty()) {
            return true;
        }

        final String lowerCandidate = candidateText.toLowerCase(Locale.ROOT);
        final String[] tokens = query.split("\\s+");
        for (final String token : tokens) {
            if (token.isEmpty()) {
                continue;
            }
            if (!matchesSingleToken(lowerCandidate, token)) {
                return false;
            }
        }
        return true;
    }

    private static boolean matchesSingleToken(final String lowerCandidate, final String token) {
        if (lowerCandidate.contains(token)) {
            return true;
        }

        final Method jecMethod = resolveJecMatchMethod();
        if (jecMethod != null) {
            try {
                return Boolean.TRUE.equals(jecMethod.invoke(null, lowerCandidate, token));
            } catch (final ReflectiveOperationException ignored) {
            }
        }

        return false;
    }

    @Nullable
    private static Method resolveJecMatchMethod() {
        if (!jecChecked) {
            jecChecked = true;
            try {
                final Class<?> clazz = Class.forName("me.towdium.jecharacters.utils.Match");
                jecMatchContains = clazz.getMethod("contains", String.class, CharSequence.class);
            } catch (final ReflectiveOperationException ignored) {
                jecMatchContains = null;
            }
        }
        return jecMatchContains;
    }
}
