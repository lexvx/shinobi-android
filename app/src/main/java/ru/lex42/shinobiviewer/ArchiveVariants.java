package ru.lex42.shinobiviewer;

import java.io.IOException;
import java.net.URI;
import org.json.JSONObject;

/** Server-selected recording variants, constrained to the authenticated origin. */
final class ArchiveVariants {
    final URI original;
    final URI low;

    private ArchiveVariants(URI original, URI low) {
        this.original = original;
        this.low = low;
    }

    static ArchiveVariants parse(URI recording, JSONObject response) throws IOException {
        JSONObject variants = response.optJSONObject("variants");
        if (!response.optBoolean("ok") || variants == null)
            throw new IOException("Не удалось получить варианты записи.");
        URI original = resolve(recording, variants.optJSONObject("original"));
        if (original == null) throw new IOException("Оригинал записи недоступен.");
        return new ArchiveVariants(original, resolve(recording, variants.optJSONObject("low")));
    }

    private static URI resolve(URI base, JSONObject variant) throws IOException {
        if (variant == null) return null;
        try {
            String value = variant.optString("url", "");
            if (value.isEmpty()) return null;
            URI result = base.resolve(value);
            if (result.getUserInfo() != null || result.getFragment() != null ||
                base.getHost() == null || !base.getHost().equalsIgnoreCase(result.getHost()) ||
                !base.getScheme().equalsIgnoreCase(result.getScheme()) || port(base) != port(result))
                throw new IOException("Сервер вернул недопустимый адрес записи.");
            return result;
        } catch (IllegalArgumentException ex) {
            throw new IOException("Некорректный адрес записи.", ex);
        }
    }

    private static int port(URI uri) {
        return uri.getPort() >= 0 ? uri.getPort() : "https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80;
    }
}
