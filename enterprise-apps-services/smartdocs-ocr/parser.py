"""Position-aware invoice parsing, normalization, confidence and arithmetic validation."""
from __future__ import annotations

import math
import re
import unicodedata
from datetime import datetime
from difflib import get_close_matches
from difflib import SequenceMatcher
from itertools import permutations
from typing import Any

FIELD_ALIASES = {
    "invoice_number": (
        "invoice no", "invoice number", "invoice #", "no invoice",
        "nomor invoice", "no faktur", "faktur no", "no order",
        "receipt no", "check no", "transaction no", "trx no",
        "nomor transaksi", "no transaksi", "nomor struk", "no struk",
    ),
    "invoice_date": ("invoice date", "tanggal invoice", "tgl invoice", "date", "tanggal"),
    "due_date": ("due date", "jatuh tempo", "tanggal jatuh tempo"),
    "vendor": ("vendor", "supplier", "company", "perusahaan", "penjual"),
    "tax": ("tax", "ppn", "vat", "pajak"),
    "discount": ("discount", "diskon", "potongan"),
    "subtotal": ("subtotal", "sub total"),
    "grand_total": ("grand total", "total pembayaran", "total bayar", "amount due", "net total", "total"),
}
TABLE_ALIASES = {
    "description": (
        "description", "deskripsi", "nama barang", "nama produk", "nama jasa",
        "uraian", "rincian", "nama", "barang", "item", "product", "produk",
        "keterangan", "particular", "jenis pekerjaan", "pekerjaan", "service",
        "services",
    ),
    "qty": (
        "qty", "quantity", "jumlah", "jml", "kuantitas", "kuantum", "banyak",
        "banyaknya", "volume", "vol",
    ),
    "unit": ("unit", "satuan", "sat", "uom"),
    "price": (
        "unit price", "harga satuan", "harga unit", "harga per unit",
        "price", "harga", "har", "rate", "tarif", "biaya satuan",
    ),
    "subtotal": (
        "subtotal", "amount", "jumlah harga", "total harga", "jumlah biaya",
        "nilai", "nominal", "total",
    ),
}
HEADER_TOKEN_ALIASES = {
    "description": (
        "description", "deskripsi", "nama", "barang", "item", "product",
        "produk", "keterangan", "particular", "uraian", "rincian",
    ),
    "qty": (
        "qty", "quantity", "jumlah", "jml", "kuantitas", "kuantum", "banyak",
        "banyaknya", "volume", "vol",
    ),
    "unit": ("unit", "satuan", "sat", "uom"),
    "price": (
        "unit price", "harga satuan", "harga unit", "price", "harga",
        "har", "rate", "tarif", "biaya",
    ),
    "subtotal": ("subtotal", "amount", "total", "nilai", "nominal"),
}
DYNAMIC_FIELD_ALIASES = {
    "vendor": ("vendor", "supplier", "company", "perusahaan", "penjual"),
    "customer": (
        "customer",
        "pelanggan",
        "pembeli",
        "bill to",
        "ship to",
        "tagihan kepada",
        "ditagihkan kepada",
    ),
    "name": ("nama", "name"),
    "address": ("alamat", "address"),
    "city": ("kota", "city"),
    "phone": ("telepon", "telephone", "phone", "telp"),
    "invoice_number": FIELD_ALIASES["invoice_number"],
    "invoice_date": FIELD_ALIASES["invoice_date"],
    "due_date": FIELD_ALIASES["due_date"],
    "currency": ("currency", "mata uang"),
    "purchase_order": ("po number", "po no", "purchase order", "no order", "order no"),
    "tax": FIELD_ALIASES["tax"],
    "discount": FIELD_ALIASES["discount"],
    "subtotal": ("subtotal", "sub total"),
    "grand_total": FIELD_ALIASES["grand_total"],
}
WORD_CORRECTIONS = {
    "involce": "invoice", "invo1ce": "invoice", "suppiler": "supplier",
    "suppller": "supplier", "quantlty": "quantity", "subtota1": "subtotal",
}


def normalize_text(value: str) -> str:
    value = unicodedata.normalize("NFKC", value or "")
    value = value.replace("—", "-").replace("–", "-").replace("|", "I")
    words = [WORD_CORRECTIONS.get(word.lower(), word) for word in value.split()]
    return re.sub(r"\s+", " ", " ".join(words)).strip()


def _key(value: str) -> str:
    return re.sub(r"[^a-z0-9#]+", " ", normalize_text(value).lower()).strip()


def _group_lines(tokens: list[dict[str, Any]]) -> list[dict[str, Any]]:
    lines: list[dict[str, Any]] = []
    for token in sorted(tokens, key=lambda item: (item.get("page", 1), item["cy"], item["x0"])):
        matched = None
        for line in reversed(lines[-8:]):
            if line["page"] != token.get("page", 1):
                continue
            tolerance = max(8.0, min(line["height"], token["y1"] - token["y0"]) * 0.58)
            if abs(line["cy"] - token["cy"]) <= tolerance:
                matched = line
                break
        if matched is None:
            lines.append({
                "page": token.get("page", 1), "tokens": [token], "cy": token["cy"],
                "height": max(8.0, token["y1"] - token["y0"]),
            })
        else:
            matched["tokens"].append(token)
            count = len(matched["tokens"])
            matched["cy"] = ((matched["cy"] * (count - 1)) + token["cy"]) / count
    for line in lines:
        line["tokens"].sort(key=lambda token: token["x0"])
        line["text"] = " ".join(token["text"] for token in line["tokens"])
        line["confidence"] = round(sum(token["confidence"] for token in line["tokens"]) / len(line["tokens"]), 2)
        line["x0"] = min(token["x0"] for token in line["tokens"])
        line["x1"] = max(token["x1"] for token in line["tokens"])
    return sorted(lines, key=lambda line: (line["page"], line["cy"]))


def _money(value: Any) -> float:
    text = str(value or "")
    text = re.sub(r"(?i)^\s*(?:rp|idr|usd|eur|sgd|aud|re|ro)[.\s]*", "", text)
    if re.search(r"\d", text):
        text = text.translate(str.maketrans({
            "O": "0", "o": "0", "I": "1", "l": "1", "ı": "1",
            "z": "2", "Z": "2", "s": "5", "S": "5", "B": "8",
            "D": "0", "d": "0", "Q": "0", "q": "0", "C": "0", "c": "0",
        }))
    # Indonesian accounting commonly appends ",-" to indicate zero cents.
    text = re.sub(r"([.,])\s*[-–—]\s*$", "", text)
    text = re.sub(r"[^\d,.\-]", "", text)
    if text and not text.startswith("-"):
        # Handwritten receipts often write `75.000-` or OCR drops the comma
        # from `75.000,-`. Treat a trailing dash as a cents marker, not as a
        # negative sign.
        text = re.sub(r"[-–—]+$", "", text)
    if not text:
        return 0.0
    # OCR-safe decimal/thousand separator handling for Indonesian and international invoices.
    repeated_decimal = re.fullmatch(
        r"(-?\d{1,3}(?:([.,])\d{3})+)\2(\d{2})", text
    )
    if repeated_decimal:
        # OCR sometimes turns 20,000.00 into 20.000.00.  The final two
        # digits are cents; preceding three-digit groups are thousands.
        integer_part = re.sub(r"[.,]", "", repeated_decimal.group(1))
        text = f"{integer_part}.{repeated_decimal.group(3)}"
    elif "," in text and "." in text:
        if len(re.split(r"[,.]", text)[-1]) == 3:
            text = text.replace(".", "").replace(",", "")
        else:
            decimal = "," if text.rfind(",") > text.rfind(".") else "."
            thousands = "." if decimal == "," else ","
            text = text.replace(thousands, "").replace(decimal, ".")
    elif text.count(".") > 1 or text.count(",") > 1:
        text = text.replace(".", "").replace(",", "")
    elif "." in text or "," in text:
        separator = "." if "." in text else ","
        tail = text.rsplit(separator, 1)[-1]
        text = text.replace(separator, "" if len(tail) == 3 else ".")
    try:
        return float(text)
    except ValueError:
        return 0.0


def _number(value: Any) -> float:
    text = str(value or "").strip()
    if re.search(r"\d", text):
        text = text.translate(
            str.maketrans(
                {
                    "O": "0",
                    "o": "0",
                    "I": "1",
                    "l": "1",
                    "S": "5",
                    "s": "5",
                    "B": "8",
                    "Z": "2",
                    "z": "2",
                    "D": "0",
                    "d": "0",
                    "Q": "0",
                    "q": "0",
                    "C": "0",
                    "c": "0",
                }
            )
        )
    match = re.search(r"-?\d+(?:[.,]\d+)?", text)
    if not match:
        return 0.0
    candidate = match.group(0)
    if re.fullmatch(r"-?\d{1,3}(?:[.,]\d{3})+", candidate):
        candidate = candidate.replace(".", "").replace(",", "")
    else:
        candidate = candidate.replace(",", ".")
    return float(candidate)


def _confidence(tokens: list[dict[str, Any]]) -> float:
    return round(sum(float(token["confidence"]) for token in tokens) / len(tokens), 2) if tokens else 0.0


def _match_alias(text: str, aliases: tuple[str, ...]) -> bool:
    normalized = _key(text)
    if any(alias in normalized for alias in aliases):
        return True
    words = normalized.split()
    return any(get_close_matches(alias, [normalized, *words], n=1, cutoff=0.86) for alias in aliases)


def _value_after_label(line: dict[str, Any], aliases: tuple[str, ...]) -> tuple[str, list[dict[str, Any]]]:
    tokens = line["tokens"]
    normalized = _key(line["text"])
    for alias in sorted(aliases, key=len, reverse=True):
        index = normalized.find(alias)
        if index < 0:
            continue
        raw = line["text"]
        match = re.search(re.escape(alias), _key(raw))
        if match:
            # Prefer tokens positioned to the right of the label.
            label_tokens = [token for token in tokens if any(part in _key(token["text"]) for part in alias.split())]
            boundary = max((token["x1"] for token in label_tokens), default=line["x0"])
            values = [token for token in tokens if token["x0"] >= boundary - 2 and token not in label_tokens]
            if values:
                return normalize_text(" ".join(token["text"] for token in values)).strip(" :#-"), values
        value = re.split(r"[:#]", raw, maxsplit=1)
        if len(value) == 2:
            return normalize_text(value[1]), tokens
    return "", []


def _looks_like_invoice_number(value: Any) -> bool:
    """Reject prose/date lines accidentally paired with an invoice label."""
    text = normalize_text(str(value or "")).strip(" :#")
    key = _key(text)
    if not text or len(text) > 48 or not re.search(r"\d", text):
        return False
    if re.search(
        r"\b(?:selesai|pembayaran|payment|rekening|account|alamat|address|"
        r"telepon|phone|tanggal|jatuh\s+tempo|kepada|customer|pelanggan|yth)\b",
        key,
    ):
        return False
    date_like = re.search(
        r"\b(?:\d{1,2}[/.\-]\d{1,2}[/.\-]\d{2,4}|"
        r"\d{4}[/.\-]\d{1,2}[/.\-]\d{1,2}|"
        r"\d{1,2}\s+[A-Za-z]{3,12}\s+\d{2,4})\b",
        text,
    )
    explicit_invoice_prefix = re.search(
        r"\b(?:inv|invoice|faktur|nota|order|po)\b", key
    )
    alpha_words = re.findall(r"[A-Za-z]{2,}", text)
    if date_like and not explicit_invoice_prefix and (
        alpha_words
        or re.fullmatch(r"\s*\d{1,4}[/.\-]\d{1,2}[/.\-]\d{1,4}\s*", text)
    ):
        return False
    return len(alpha_words) <= 4


def _extract_labeled(lines: list[dict[str, Any]], field: str) -> tuple[str, float]:
    aliases = FIELD_ALIASES[field]
    for index, line in enumerate(lines):
        line_key = _key(line["text"])
        if field in {"invoice_number", "invoice_date", "due_date"} and line_key in {"invoice", "faktur", "nota"}:
            continue
        if field == "due_date" and not re.search(r"\b(?:jatuh|tempo|due)\b", line_key):
            continue
        fuzzy_ratio = max(
            [
                SequenceMatcher(None, _key(token["text"]), alias_word).ratio()
                for token in line["tokens"] for alias in aliases for alias_word in alias.split()
                if _key(token["text"]) and len(alias_word) >= 3
            ] or [0.0]
        )
        if not _match_alias(line["text"], aliases) and fuzzy_ratio < 0.68:
            continue
        if field in {"invoice_date", "due_date"}:
            date_pattern = (
                r"\d{1,2}[/.\-]\d{1,2}[/.\-]\d{2,4}|\d{4}[/.\-]\d{1,2}[/.\-]\d{1,2}|"
                r"\d{1,2}[\s\-]+[A-Za-z]{3,12}[\s\-]+\d{2,4}"
            )
            date_match = re.search(date_pattern, line["text"])
            date_line = line
            if not date_match and index + 1 < len(lines):
                candidate_line = lines[index + 1]
                if candidate_line["page"] == line["page"] and candidate_line["cy"] - line["cy"] < line["height"] * 2.2:
                    date_match = re.search(date_pattern, candidate_line["text"])
                    date_line = candidate_line
            if date_match:
                date_tokens = [token for token in date_line["tokens"] if any(char.isdigit() for char in token["text"])]
                return date_match.group(0), _confidence(date_tokens)
            continue
        value, value_tokens = _value_after_label(line, aliases)
        if not value:
            # Fuzzy OCR label (e.g. "Tangsal") cannot be split textually. Use
            # tokens to the right of the most label-like token.
            best_token = None
            best_ratio = 0.0
            for token in line["tokens"]:
                token_key = _key(token["text"])
                for alias in aliases:
                    for alias_word in alias.split():
                        ratio = SequenceMatcher(None, token_key, alias_word).ratio()
                        if ratio > best_ratio:
                            best_token, best_ratio = token, ratio
            if best_token is not None and best_ratio >= 0.68:
                value_tokens = [token for token in line["tokens"] if token["x0"] >= best_token["x1"] - 2 and token is not best_token]
                value = normalize_text(" ".join(token["text"] for token in value_tokens)).strip(" :#-")
        if not value and index + 1 < len(lines):
            next_line = lines[index + 1]
            if next_line["page"] == line["page"] and next_line["cy"] - line["cy"] < line["height"] * 2.2:
                value, value_tokens = next_line["text"], next_line["tokens"]
        if field == "invoice_number" and value and not _looks_like_invoice_number(value):
            continue
        if value:
            return value, _confidence(value_tokens)
    return "", 0.0


def _fallback_invoice_identifier(
    lines: list[dict[str, Any]],
) -> tuple[str, float]:
    """Recover explicit INV-prefixed identifiers missed by label/value pairing."""
    long_numeric_candidates: list[tuple[int, float, str]] = []
    for line in lines:
        for token in line["tokens"]:
            text = normalize_text(str(token.get("text") or "")).strip(" :#")
            match = re.search(
                r"(?i)\b(?:INV|FAK)[A-Z0-9./_-]*\d[A-Z0-9./_-]*\b",
                text,
            )
            if match and _looks_like_invoice_number(match.group(0)):
                return match.group(0), float(token.get("confidence") or 0)
            receipt_match = re.match(
                r"(?i)^\s*no\.?\s*[:#-]?\s*([A-Z0-9][A-Z0-9./_-]*\d[A-Z0-9./_-]*)\s*$",
                text,
            )
            if receipt_match and _looks_like_invoice_number(
                receipt_match.group(1)
            ):
                return receipt_match.group(1), float(token.get("confidence") or 0)
            compact_digits = re.sub(r"\D", "", text)
            if (
                text == compact_digits
                and len(compact_digits) >= 16
                and not compact_digits.startswith(("08", "62"))
            ):
                long_numeric_candidates.append(
                    (
                        len(compact_digits),
                        float(token.get("confidence") or 0),
                        compact_digits,
                    )
                )
    if long_numeric_candidates:
        _, confidence, identifier = max(long_numeric_candidates)
        return identifier, confidence
    return "", 0.0


def _date(value: str) -> str:
    match = re.search(
        r"(\d{1,2}[/.\-]\d{1,2}[/.\-]\d{2,4}|\d{4}[/.\-]\d{1,2}[/.\-]\d{1,2}|"
        r"\d{1,2}[\s\-]+[A-Za-z]+[\s\-]+\d{2,4})", value
    )
    if not match:
        return value.strip(" :")
    candidate = match.group(1)
    for fmt in (
        "%d/%m/%Y", "%d-%m-%Y", "%d.%m.%Y", "%Y-%m-%d", "%Y/%m/%d",
        "%d/%m/%y", "%d-%m-%y", "%d-%b-%Y", "%d %b %Y", "%d %B %Y",
        "%d-%b-%y", "%d %b %y", "%d %B %y",
    ):
        try:
            return datetime.strptime(candidate, fmt).strftime("%Y-%m-%d")
        except ValueError:
            pass
    return candidate


def _detect_currency(text: str) -> tuple[str, float]:
    patterns = (("IDR", r"\bIDR\b|\bRp\.?"), ("USD", r"\bUSD\b|\$"), ("EUR", r"\bEUR\b|€"), ("SGD", r"\bSGD\b"), ("AUD", r"\bAUD\b"))
    for currency, pattern in patterns:
        if re.search(pattern, text, re.I):
            return currency, 96.0
    return "IDR", 70.0


_CATEGORY_KEYWORDS: dict[str, tuple[str, ...]] = {
    "Konsumsi": (
        "bakso", "baso", "mie", "mi ayam", "mie ayam", "noodle",
        "pangsit", "siomay", "batagor", "soto", "nasi", "ayam",
        "ikan", "daging", "telur", "sayur", "roti", "bread",
        "burger", "pizza", "pudding", "croissant", "choco",
        "coklat", "chocolate", "kopi", "coffee", "teh", "tea",
        "lemon tea", "jus", "juice", "minum", "minuman", "makan",
        "makanan", "food", "beverage", "drink", "snack", "resto",
        "restaurant", "restoran", "cafe", "kafe", "warung",
        "catering", "konsumsi", "breadtalk",
    ),
    "Jasa": (
        "jasa", "service", "servis", "consulting", "konsultasi",
        "training", "pelatihan", "maintenance", "perawatan",
        "design", "desain", "development", "instalasi", "sewa",
        "repair", "reparasi", "biaya layanan", "fee",
    ),
    "Utilitas": (
        "listrik", "pln", "pdam", "air", "telkom", "telepon",
        "internet", "wifi", "utilitas", "gas", "token listrik",
        "pulsa", "data", "tagihan listrik", "tagihan air",
    ),
    "Transportasi": (
        "ongkir", "ekspedisi", "kurir", "kirim", "pengiriman",
        "transport", "transportasi", "taksi", "taxi", "grab",
        "gojek", "logistik", "freight", "cargo", "parkir",
        "tol", "travel", "ticket", "tiket", "pax", "tour",
    ),
    "Barang": (
        "barang", "produk", "product", "alat", "printer", "tinta",
        "epson", "komputer", "laptop", "mouse", "keyboard",
        "kertas", "pensil", "buku", "binder", "seragam", "baju",
        "kaos", "sepatu", "dress", "shirt",
    ),
}


def _infer_category(
    vendor: str,
    items: list[dict[str, Any]],
    raw_text: str = "",
) -> tuple[str, float]:
    """Infer a SmartDocs category from vendor, line items, and OCR text."""
    item_text = " ".join(
        str(item.get("item") or item.get("description") or "")
        for item in items
    )
    combined = _key(" ".join([vendor or "", item_text, raw_text or ""]))
    if not combined:
        return "Barang", 50.0

    scores = {category: 0 for category in _CATEGORY_KEYWORDS}
    for category, keywords in _CATEGORY_KEYWORDS.items():
        for keyword in keywords:
            keyword_key = _key(keyword)
            if not keyword_key:
                continue
            if re.search(rf"(?<![a-z0-9]){re.escape(keyword_key)}(?![a-z0-9])", combined):
                scores[category] += 5 if category != "Barang" else 3
            elif keyword_key in combined:
                scores[category] += 2

    category, score = max(scores.items(), key=lambda item: item[1])
    if score <= 0:
        return ("Barang", 58.0) if items else ("Barang", 45.0)
    confidence = min(96.0, 62.0 + score * 4.0)
    return category, confidence


def _looks_like_address(value: Any) -> bool:
    key = _key(str(value or ""))
    return bool(
        re.search(
            r"^(?:jl|jln|jalan|gg|gang|komplek|kompleks|blok|ruko|"
            r"kel|kelurahan|kec|kecamatan|kab|kabupaten|kota|"
            r"desa|dusun|rt|rw)\b",
            key,
        )
        or re.search(
            r"\b(?:kel|kelurahan|kec|kecamatan|kab|kabupaten|"
            r"kota|provinsi|jakarta|surabaya|bandung|semampir|"
            r"medokan|sudirman|soekarno)\b",
            key,
        )
    )


def _extract_vendor(lines: list[dict[str, Any]]) -> tuple[str, float]:
    labeled, confidence = _extract_labeled(lines, "vendor")
    if labeled:
        return labeled[:150], confidence
    ignore = (
        "receipt", "customer", "pelanggan", "bill to", "ship to",
        "tagihan kepada", "kepada yth", "tanggal", "date",
    )
    candidates = []
    business_candidates = []
    recipient_index = next(
        (
            index
            for index, line in enumerate(lines[:16])
            if re.match(
                r"^(?:kepada(?:\s+yth)?|customer|pelanggan|bill to|ship to|"
                r"tagihan kepada|ditagihkan kepada)\b",
                _key(line["text"]),
            )
        ),
        None,
    )
    candidate_lines = (
        lines[: min(12, recipient_index)]
        if recipient_index is not None
        else lines[:12]
    )
    for line in candidate_lines:
        text = normalize_text(line["text"]).strip(" :-")
        # Logos are sometimes read as an isolated digit immediately before a
        # store name (for example a cart icon becoming "3").
        text = re.sub(r"^\d{1,2}\s+(?=[A-Za-z])", "", text).strip()
        # Company names are frequently printed on the same visual line as the
        # large INVOICE title. Remove the title, not the remaining company.
        text = re.sub(r"(?i)\b(?:invoice|faktur|nota)\b", " ", text)
        text = re.sub(r"\s+", " ", text).strip(" :-")
        text = re.sub(
            r"\s+\d{1,2}[/.\-]\d{1,2}[/.\-]\d{2,4}.*$",
            "",
            text,
        ).strip(" :-")
        text_key = _key(text)
        if (
            len(text) < 3
            or any(word in text.lower() for word in ignore)
            or _looks_like_address(text)
            or bool(
                re.fullmatch(
                    r"\d{1,2}[/.\-]\d{1,2}[/.\-]\d{2,4}",
                    text.strip(),
                )
            )
            or re.search(r"\d{5,}", text)
            or (
                _looks_like_invoice_number(text)
                and not re.search(r"^(?:pt|cv|ud)\b|\b(?:ltd|llc|inc|co)\b", text_key)
            )
            or re.match(r"^(?:nama|name|alamat|address|kota|city|telepon|phone)\b", text_key)
        ):
            continue
        legal_entity = bool(
            re.search(r"^(?:pt|cv|ud)\b|\b(?:ltd|llc|inc)\b", text_key, re.I)
        )
        store_name = bool(re.search(r"\b(?:toko|store)\b", text_key, re.I))
        company_name = bool(
            re.search(
                r"(?:&\s*co\.?\b|\b(?:company|group|corp|corporation)\b)",
                text,
                re.I,
            )
        )
        business_bonus = (
            38 if legal_entity else (22 if company_name else (12 if store_name else 0))
        )
        candidate = (line["confidence"] + business_bonus - len(text) * 0.03, text, line["confidence"])
        candidates.append(candidate)
        if business_bonus:
            business_candidates.append(candidate)
    if business_candidates:
        selected = max(business_candidates)
        return selected[1][:150], selected[2]
    return (max(candidates)[1][:150], max(candidates)[2]) if candidates else ("", 0.0)


def _column_from_header(header: dict[str, Any]) -> dict[str, float]:
    columns: dict[str, float] = {}
    tokens = header["tokens"]
    matches_by_field: dict[str, list[dict[str, Any]]] = {field: [] for field in TABLE_ALIASES}
    for token in tokens:
        token_key = _key(token["text"])
        if not token_key:
            continue
        scored: list[tuple[float, str]] = []
        for field, aliases in HEADER_TOKEN_ALIASES.items():
            score = max(
                SequenceMatcher(None, token_key, alias).ratio()
                for alias in aliases if len(alias) >= 3
            )
            if any(alias == token_key or alias in token_key for alias in aliases):
                score += 0.35
            scored.append((score, field))
        score, field = max(scored)
        if score >= 0.68:
            matches_by_field[field].append(token)

    # Indonesian headers commonly use both "Kuantitas" and "Jumlah".  In
    # that context Jumlah is the line amount, not a second quantity column.
    # Resolve it before averaging token positions so the two columns do not
    # collapse into a point midway between them.
    explicit_qty_matches = [
        token
        for token in tokens
        if re.fullmatch(r"(?:qty|quantity|kuantitas|kuantum|jml|banyak(?:nya)?)", _key(token["text"]))
    ]
    ambiguous_amount_matches = [
        token
        for token in tokens
        if re.fullmatch(r"(?:jumlah|total|amount|subtotal)", _key(token["text"]))
    ]
    if explicit_qty_matches and ambiguous_amount_matches:
        explicit_qty_x = sum(token["cx"] for token in explicit_qty_matches) / len(explicit_qty_matches)
        right_amounts = [token for token in ambiguous_amount_matches if token["cx"] > explicit_qty_x]
        if right_amounts:
            matches_by_field["qty"] = explicit_qty_matches
            matches_by_field["subtotal"] = right_amounts
    for field, matches in matches_by_field.items():
        if matches:
            columns[field] = sum(token["cx"] for token in matches) / len(matches)
    # Handle split labels such as "Harga Satuan".
    text = _key(header["text"])
    for field, aliases in TABLE_ALIASES.items():
        if field not in columns and any(alias in text for alias in aliases):
            alias_words = set(next(alias for alias in aliases if alias in text).split())
            matches = [token for token in tokens if _key(token["text"]) in alias_words]
            if matches:
                columns[field] = sum(token["cx"] for token in matches) / len(matches)
    return columns


def _find_table(lines: list[dict[str, Any]]) -> tuple[int, dict[str, float]]:
    best = (-1, {}, 0)
    for index, line in enumerate(lines):
        candidates = [(index, line)]
        if index + 1 < len(lines):
            next_line = lines[index + 1]
            gap = next_line["cy"] - line["cy"]
            next_has_header_role = bool(_column_from_header(next_line))
            next_has_numeric_value = any(
                re.search(r"\d", str(token.get("text") or ""))
                for token in next_line["tokens"]
            )
            if (
                next_line["page"] == line["page"]
                and 0 < gap <= max(line["height"], next_line["height"]) * 1.65
                and next_has_header_role
                and not next_has_numeric_value
            ):
                merged_tokens = sorted(
                    [*line["tokens"], *next_line["tokens"]],
                    key=lambda token: token["x0"],
                )
                candidates.append(
                    (
                        index + 1,
                        {
                            "page": line["page"],
                            "tokens": merged_tokens,
                            "text": " ".join(
                                str(token.get("text") or "")
                                for token in merged_tokens
                            ),
                            "confidence": _confidence(merged_tokens),
                            "x0": min(token["x0"] for token in merged_tokens),
                            "x1": max(token["x1"] for token in merged_tokens),
                            "cy": (line["cy"] + next_line["cy"]) / 2,
                            "height": max(
                                line["height"],
                                max(token["y1"] for token in merged_tokens)
                                - min(token["y0"] for token in merged_tokens),
                            ),
                        },
                    )
                )
        for end_index, candidate_line in candidates:
            columns = _column_from_header(candidate_line)
            score = len(columns) + (2 if "description" in columns else 0)
            if score > best[2] and len(columns) >= 2:
                best = (end_index, columns, score)
    return best[0], best[1]


def semantic_role(label: str) -> str | None:
    """Map a source column to an optional semantic role without renaming it."""
    label_key = _key(label)
    best: tuple[float, str | None] = (0.0, None)
    for role, aliases in TABLE_ALIASES.items():
        for alias in aliases:
            if alias == label_key:
                score = 1.25
            elif alias in label_key:
                score = 0.9 + min(0.2, len(alias) / max(len(label_key), 1) * 0.2)
            else:
                score = SequenceMatcher(None, label_key, alias).ratio()
            if score > best[0]:
                best = (score, role)
    # A low fuzzy threshold turns unrelated headers such as "Lokasi" into a
    # monetary column. OCR misspellings remain well above this stricter bound
    # (for example "Prce" -> "price"), while unrelated business labels do not.
    return best[1] if best[0] >= 0.68 else None


def _bill_to_fields(
    lines: list[dict[str, Any]], table_header_index: int
) -> list[dict[str, Any]]:
    """Recover an unlabeled recipient block headed by Bill To/Tagihan Kepada."""
    heading_pattern = re.compile(
        r"^(?:bill to|ship to|tagihan kepada|ditagihkan kepada|kepada pelanggan)$"
    )
    for heading_index, heading in enumerate(lines):
        if table_header_index >= 0 and heading_index >= table_header_index:
            break
        if not heading_pattern.fullmatch(_key(heading["text"])):
            continue

        block: list[dict[str, Any]] = []
        end_index = (
            table_header_index
            if table_header_index > heading_index
            else min(len(lines), heading_index + 7)
        )
        for line in lines[heading_index + 1 : end_index]:
            if line["page"] != heading["page"]:
                break
            if line["cy"] - heading["cy"] > max(heading["height"], 12) * 10:
                break
            line_key = _key(line["text"])
            if any(
                _match_alias(line_key, FIELD_ALIASES[field])
                for field in ("invoice_number", "invoice_date", "due_date")
            ):
                break
            block.append(line)
        if not block:
            continue

        phone_line = next(
            (
                line
                for line in block
                if re.match(r"^(?:telp|telepon|telephone|phone|wa|whatsapp)\b", _key(line["text"]))
            ),
            None,
        )
        email_line = next(
            (line for line in block if re.search(r"[\w.+-]+@[\w.-]+\.\w+", line["text"])),
            None,
        )
        identity_lines = [
            line for line in block if line is not phone_line and line is not email_line
        ]
        if not identity_lines:
            continue

        first = normalize_text(identity_lines[0]["text"]).strip(" ,:-")
        address_marker = re.search(
            r"(?i)\b(?:jl\.?|jalan|jln\.?|gg\.?|gang|kp\.?|kampung|"
            r"dk\.?|dusun|desa|kel\.?|kelurahan|kec\.?|kecamatan|"
            r"kab\.?|kabupaten|perum(?:ahan)?|komplek|kompleks)\b",
            first,
        )
        if address_marker and address_marker.start() > 1:
            customer = first[: address_marker.start()].strip(" ,:-")
            address_parts = [first[address_marker.start() :].strip(" ,:-")]
        else:
            customer = first
            address_parts = []
        address_parts.extend(
            normalize_text(line["text"]).strip(" ,:-")
            for line in identity_lines[1:]
            if normalize_text(line["text"]).strip(" ,:-")
        )

        recovered = []
        if customer:
            recovered.append(
                {
                    "key": "customer",
                    "label": normalize_text(heading["text"]),
                    "value": customer,
                    "confidence": identity_lines[0]["confidence"],
                    "page": heading["page"],
                    "source": "bill_to_block",
                }
            )
        address = ", ".join(part for part in address_parts if part)
        if address:
            recovered.append(
                {
                    "key": "address",
                    "label": "Alamat penerima",
                    "value": address,
                    "confidence": min(line["confidence"] for line in identity_lines),
                    "page": heading["page"],
                    "source": "bill_to_block",
                }
            )
        if phone_line:
            phone = re.sub(
                r"(?i)^\s*(?:telp|telepon|telephone|phone|wa|whatsapp)\s*[:.-]?\s*",
                "",
                normalize_text(phone_line["text"]),
            ).strip()
            if phone:
                recovered.append(
                    {
                        "key": "phone",
                        "label": "Telepon",
                        "value": phone,
                        "confidence": phone_line["confidence"],
                        "page": heading["page"],
                        "source": "bill_to_block",
                    }
                )
        return recovered
    return []


def _dynamic_fields(lines: list[dict[str, Any]], table_header_index: int) -> list[dict[str, Any]]:
    """Return only label/value pairs visibly present in the document."""
    detected: list[dict[str, Any]] = []
    for line_index, line in enumerate(lines):
        # Item rows are not header fields. Totals below a table remain eligible.
        if table_header_index >= 0 and line_index == table_header_index:
            continue
        if table_header_index >= 0 and line_index > table_header_index:
            line_key = _key(line["text"])
            total_aliases = (
                *FIELD_ALIASES["tax"], *FIELD_ALIASES["discount"],
                *DYNAMIC_FIELD_ALIASES["subtotal"], *FIELD_ALIASES["grand_total"],
            )
            label_tokens = [token for token in line["tokens"] if not re.search(r"\d", token["text"])]
            fuzzy_total = max(
                [
                    SequenceMatcher(None, _key(token["text"]), alias_word).ratio()
                    for token in label_tokens for alias in total_aliases for alias_word in alias.split()
                    if _key(token["text"])
                ] or [0.0]
            )
            if not any(alias in line_key for alias in total_aliases) and fuzzy_total < 0.72:
                continue

        starts: list[dict[str, Any]] = []
        for canonical, aliases in DYNAMIC_FIELD_ALIASES.items():
            best_tokens: list[dict[str, Any]] = []
            best_score = 0.0
            for alias in aliases:
                alias_words = alias.split()
                for start in range(len(line["tokens"])):
                    for token_count in range(1, min(len(alias_words), len(line["tokens"]) - start) + 1):
                        candidate = line["tokens"][start:start + token_count]
                        candidate_key = _key(" ".join(token["text"] for token in candidate))
                        score = SequenceMatcher(None, candidate_key, alias).ratio()
                        if alias in candidate_key:
                            score += 0.25
                        if score > best_score:
                            best_tokens, best_score = candidate, score
            if best_tokens and best_score >= 0.72:
                starts.append({
                    "canonical_key": canonical,
                    "tokens": best_tokens,
                    "x0": min(token["x0"] for token in best_tokens),
                    "x1": max(token["x1"] for token in best_tokens),
                    "score": best_score,
                })

        # Generic single "Label: value" fields remain dynamic even when unknown.
        if not starts and ":" in line["text"]:
            split = line["text"].split(":", 1)
            label = normalize_text(split[0]).strip()
            value = normalize_text(split[1]).strip()
            if label and value and len(label.split()) <= 5 and not re.search(r"\d", label):
                detected.append({
                    "key": f"field_{len(detected) + 1}",
                    "label": label,
                    "value": value,
                    "confidence": line["confidence"],
                    "page": line["page"],
                    "source": "document",
                })
            continue

        non_overlapping: list[dict[str, Any]] = []
        for candidate in sorted(starts, key=lambda item: item["score"], reverse=True):
            if any(
                candidate["x0"] < accepted["x1"] and accepted["x0"] < candidate["x1"]
                for accepted in non_overlapping
            ):
                continue
            non_overlapping.append(candidate)
        starts = sorted(non_overlapping, key=lambda item: item["x0"])
        for position, field in enumerate(starts):
            next_x = starts[position + 1]["x0"] if position + 1 < len(starts) else float("inf")
            value_tokens = [
                token for token in line["tokens"]
                if token["x0"] >= field["x1"] - 2 and token["x0"] < next_x
                and token not in field["tokens"]
            ]
            value = normalize_text(" ".join(token["text"] for token in value_tokens)).strip(" :#-")
            inline_label_text = ""
            if not value and ":" in line["text"]:
                inline_label, inline_value = line["text"].split(":", 1)
                aliases = DYNAMIC_FIELD_ALIASES[field["canonical_key"]]
                if _match_alias(inline_label, aliases):
                    inline_label_text = normalize_text(inline_label).strip(" :#-")
                    value = normalize_text(inline_value).strip(" :#-")
                    value_tokens = line["tokens"]
            if (
                not value
                and len(starts) == 1
                and line_index + 1 < len(lines)
                and (table_header_index < 0 or line_index + 1 < table_header_index)
            ):
                next_line = lines[line_index + 1]
                next_key = _key(next_line["text"])
                next_is_label = any(
                    _match_alias(next_key, aliases)
                    for aliases in DYNAMIC_FIELD_ALIASES.values()
                )
                close_below = (
                    next_line["page"] == line["page"]
                    and 0 < next_line["cy"] - line["cy"] < line["height"] * 2.8
                )
                aligned = next_line["x0"] >= field["x0"] - line["height"] * 1.5
                if close_below and aligned and not next_is_label:
                    value = normalize_text(next_line["text"]).strip(" :#-")
                    value_tokens = next_line["tokens"]
            if not value:
                continue
            label = inline_label_text or normalize_text(
                " ".join(token["text"] for token in field["tokens"])
            ).strip(" :#-")
            detected.append({
                "key": field["canonical_key"],
                "label": label,
                "value": value,
                "confidence": _confidence(value_tokens),
                "page": line["page"],
                "source": "document",
            })

    bill_to_fields = _bill_to_fields(lines, table_header_index)
    bill_to_keys = {field["key"] for field in bill_to_fields}
    detected = [
        field for field in detected if field["key"] not in bill_to_keys
    ] + bill_to_fields

    if not any(field.get("key") == "address" for field in detected):
        address_line = next(
            (
                line
                for index, line in enumerate(lines[:12])
                if (table_header_index < 0 or index < table_header_index)
                and _looks_like_address(line.get("text"))
            ),
            None,
        )
        if address_line:
            detected.append(
                {
                    "key": "address",
                    "label": "Alamat",
                    "value": normalize_text(address_line["text"]).strip(" :-"),
                    "confidence": address_line["confidence"],
                    "page": address_line["page"],
                    "source": "address_line",
                }
            )

    unique: list[dict[str, Any]] = []
    seen: set[tuple[str, str]] = set()
    for field in detected:
        identity = (_key(field["label"]), normalize_text(field["value"]).lower())
        if identity not in seen:
            seen.add(identity)
            unique.append(field)
    return unique


def build_borderless_dynamic_tables(tokens: list[dict[str, Any]]) -> list[dict[str, Any]]:
    """Best-effort dynamic structure for tables without visible grid lines."""
    lines = _group_lines(tokens)
    header_index, role_centers = _find_table(lines)
    if header_index < 0 or len(role_centers) < 2:
        return []
    header = lines[header_index]
    page_width = max(
        [
            float(token.get("page_width") or 0)
            for token in header["tokens"]
        ]
        or [0]
    )
    ordered = sorted(role_centers.items(), key=lambda pair: pair[1])
    row_number_headers = [
        token
        for token in header["tokens"]
        if re.fullmatch(r"(?:no|nomor|#)", _key(token["text"]))
        and token["cx"] < ordered[0][1]
    ]
    columns = []
    for index, (role, center) in enumerate(ordered):
        left = (
            (max(token["cx"] for token in row_number_headers) + center) / 2
            if index == 0 and row_number_headers
            else -float("inf")
            if index == 0
            else (ordered[index - 1][1] + center) / 2
        )
        right = float("inf") if index == len(ordered) - 1 else (center + ordered[index + 1][1]) / 2
        header_tokens = [token for token in header["tokens"] if left <= token["cx"] < right]
        label = normalize_text(" ".join(token["text"] for token in header_tokens))
        columns.append({
            "id": f"column_{index + 1}", "name": label, "index": index,
            # role_centers already resolves ambiguous Indonesian headers such
            # as Jumlah using their left-to-right context.
            "semantic_role": role,
            "confidence": _confidence(header_tokens), "left": left, "right": right,
        })
    rows = []
    for line in lines[header_index + 1:]:
        # Tax/discount may be ordinary columns on every item row. Stop only
        # when the physical line itself starts with a payment-summary label.
        line_summary = _summary_key(line["text"])
        description_center = role_centers.get("description", -float("inf"))
        next_center = min(
            [
                center
                for role, center in role_centers.items()
                if role != "description" and center > description_center
            ]
            or [float("inf")]
        )
        description_boundary = (description_center + next_center) / 2
        line_description = normalize_text(
            " ".join(
                token["text"]
                for token in line["tokens"]
                if token["cx"] < description_boundary
            )
        )
        summary_is_right_of_description = any(
            _summary_key_at_start(token["text"])
            and token["cx"] > description_center
            for token in line["tokens"]
        )
        if line_summary and (
            _summary_key_at_start(line["text"])
            or (
                summary_is_right_of_description
                and _non_item_reason(line_description, line_description)
            )
        ):
            break
        cells = []
        row_y0 = min(token["y0"] for token in line["tokens"])
        row_y1 = max(token["y1"] for token in line["tokens"])
        for column in columns:
            cell_tokens = [token for token in line["tokens"] if column["left"] <= token["cx"] < column["right"]]
            cell_left = (
                max(0.0, column["left"])
                if math.isfinite(column["left"])
                else 0.0
            )
            cell_right = (
                min(page_width, column["right"])
                if math.isfinite(column["right"]) and page_width
                else (
                    column["right"]
                    if math.isfinite(column["right"])
                    else page_width
                )
            )
            cells.append({
                "text": normalize_text(" ".join(token["text"] for token in cell_tokens)),
                "confidence": _confidence(cell_tokens),
                "column_index": len(cells),
                "colspan": 1,
                "rowspan": 1,
                "bbox": (
                    [
                        min(token["x0"] for token in cell_tokens),
                        min(token["y0"] for token in cell_tokens),
                        max(token["x1"] for token in cell_tokens),
                        max(token["y1"] for token in cell_tokens),
                    ]
                    if cell_tokens
                    else (
                        [cell_left, row_y0, cell_right, row_y1]
                        if cell_right > cell_left
                        else None
                    )
                ),
            })
        if any(cell["text"] for cell in cells):
            rows.append({"index": len(rows), "cells": cells})
    for column in columns:
        column.pop("left", None)
        column.pop("right", None)
    if not rows:
        return []
    return [{
        "page": header["page"], "detection_method": "layout", "columns": columns,
        "rows": rows, "column_count": len(columns), "row_count": len(rows),
    }]


def _numeric_tokens(line: dict[str, Any]) -> list[dict[str, Any]]:
    return [token for token in line["tokens"] if re.search(r"\d", token["text"])]


def _extract_items(lines: list[dict[str, Any]]) -> list[dict[str, Any]]:
    header_index, columns = _find_table(lines)
    if header_index < 0:
        return _fallback_items(lines)
    ordered_columns = sorted(columns.items(), key=lambda pair: pair[1])
    description_x = columns.get("description", ordered_columns[0][1])
    numeric_names = [name for name in ("qty", "price", "subtotal") if name in columns]
    if not numeric_names:
        return _fallback_items(lines[header_index + 1:])

    items: list[dict[str, Any]] = []
    for line in lines[header_index + 1:]:
        lower = _key(line["text"])
        if items and re.match(r"^(?:sub\s*t\w+|subtotal)\b", lower):
            break
        if any(_match_alias(lower, FIELD_ALIASES[field]) for field in ("grand_total", "tax", "discount")):
            if items:
                break
            continue
        numeric = _numeric_tokens(line)
        if not numeric:
            continuation = normalize_text(line["text"])
            if items and continuation and line["x0"] < min(columns.get("qty", 10**9), columns.get("price", 10**9)):
                items[-1]["item"] = f"{items[-1]['item']} {continuation}".strip()
                items[-1]["confidence"]["item"] = round((items[-1]["confidence"]["item"] + line["confidence"]) / 2, 2)
            continue

        assigned: dict[str, list[dict[str, Any]]] = {name: [] for name in columns}
        boundaries: list[tuple[str, float, float]] = []
        for position, (name, center) in enumerate(ordered_columns):
            left = -float("inf") if position == 0 else (ordered_columns[position - 1][1] + center) / 2
            right = float("inf") if position == len(ordered_columns) - 1 else (center + ordered_columns[position + 1][1]) / 2
            boundaries.append((name, left, right))
        for token in line["tokens"]:
            for name, left, right in boundaries:
                if left <= token["cx"] < right:
                    assigned[name].append(token)
                    break

        description_tokens = assigned.get("description", [])
        description = normalize_text(" ".join(token["text"] for token in description_tokens))
        description = re.sub(r"^\s*\d{1,3}[.)]?\s+", "", description).strip(" :-")
        qty_tokens = assigned.get("qty", [])
        price_tokens = assigned.get("price", [])
        subtotal_tokens = assigned.get("subtotal", [])
        qty = _number(" ".join(token["text"] for token in qty_tokens))
        price = _money(" ".join(token["text"] for token in price_tokens))
        subtotal = _money(" ".join(token["text"] for token in subtotal_tokens))

        # Recover a missing column using the invariant qty × unit price = subtotal.
        inferred_fields: list[str] = []
        if not subtotal and qty and price:
            subtotal = qty * price
            inferred_fields.append("total")
        elif not price and qty and subtotal:
            price = subtotal / qty
            inferred_fields.append("harga")
        elif not qty and price and subtotal and price:
            inferred = subtotal / price
            qty = inferred if abs(inferred - round(inferred)) < 0.02 else inferred
            inferred_fields.append("jumlah")
        if not qty:
            qty = 1.0
            inferred_fields.append("jumlah")
        if not description:
            left_tokens = [token for token in line["tokens"] if token["cx"] < min(columns[name] for name in numeric_names)]
            description = normalize_text(" ".join(token["text"] for token in left_tokens))
            description_tokens = left_tokens
        explicit_zero_amount = bool(
            qty > 0
            and (
                any(
                    re.search(r"\d", str(token.get("text") or ""))
                    for token in price_tokens
                )
                or any(
                    re.search(r"\d", str(token.get("text") or ""))
                    for token in subtotal_tokens
                )
            )
        )
        if not description or (
            price <= 0 and subtotal <= 0 and not explicit_zero_amount
        ):
            continue

        expected = qty * price
        difference = abs(expected - subtotal)
        tolerance = max(1.0, abs(subtotal) * 0.005)
        items.append({
            "item": description,
            "description": description,
            "qty": qty,
            "unit": normalize_text(" ".join(token["text"] for token in assigned.get("unit", []))),
            "price": price,
            "total": subtotal or expected,
            "confidence": {
                "item": _confidence(description_tokens),
                "qty": _confidence(qty_tokens),
                "unit": _confidence(assigned.get("unit", [])),
                "price": _confidence(price_tokens),
                "total": _confidence(subtotal_tokens) if subtotal_tokens else min(_confidence(qty_tokens), _confidence(price_tokens)),
            },
            "validation": {
                "valid": difference <= tolerance,
                "expected_subtotal": round(expected, 2),
                "difference": round(difference, 2),
                "inferred_fields": list(dict.fromkeys(inferred_fields)),
            },
        })
    return items


_RECEIPT_METADATA_PREFIXES = (
    "check no",
    "receipt no",
    "transaction no",
    "nomor transaksi",
    "no transaksi",
    "nomor struk",
    "no struk",
    "cashier",
    "kasir",
    "operator",
    "server",
    "table",
    "meja",
    "guest",
    "pax",
    "member",
    "customer",
    "pelanggan",
    "phone",
    "telp",
    "telepon",
    "www",
)


def _standalone_money(value: Any) -> float | None:
    """Return a value only when one OCR token is entirely a money value."""
    text = normalize_text(str(value or "")).strip()
    if not text or re.search(r"[/;:]", text):
        return None
    if re.search(r"[A-Za-z]", text) and not re.match(
        r"(?i)^\s*(?:rp|idr|usd|eur|sgd|aud)\b", text
    ):
        return None
    if not re.fullmatch(
        r"(?i)\s*(?:(?:rp|idr|usd|eur|sgd|aud)\.?\s*)?"
        r"-?\s*\d[\d.,\s]*(?:,-)?\s*",
        text,
    ):
        return None
    amount = _money(text)
    return amount if amount > 0 else None


def _receipt_summary_target(
    lines: list[dict[str, Any]], summary_index: int
) -> float:
    if summary_index < 0:
        return 0.0
    numeric = [
        _standalone_money(token.get("text"))
        for token in lines[summary_index].get("tokens") or []
    ]
    return float(next((value for value in reversed(numeric) if value), 0.0))


def _receipt_line_item(line: dict[str, Any]) -> dict[str, Any] | None:
    """Parse `qty description amount` rows used by borderless POS receipts."""
    tokens = sorted(line.get("tokens") or [], key=lambda token: token["x0"])
    if len(tokens) < 2:
        return None
    line_text = normalize_text(str(line.get("text") or ""))
    line_key = _key(line_text)
    if (
        _looks_like_document_date(line_text)
        or _non_item_reason(line_key, line_text)
        or any(
            line_key == prefix or line_key.startswith(f"{prefix} ")
            for prefix in _RECEIPT_METADATA_PREFIXES
        )
        or re.search(r"\b(?:closed|opened|change|kembali)\b", line_key)
    ):
        return None

    alpha_indexes = [
        index
        for index, token in enumerate(tokens)
        if re.search(r"[A-Za-z]", str(token.get("text") or ""))
        and _standalone_money(token.get("text")) is None
        and not re.fullmatch(
            r"(?i)\s*(?:rp|idr|usd|eur|sgd|aud)\.?\s*",
            str(token.get("text") or ""),
        )
    ]
    if not alpha_indexes:
        return None
    first_alpha, last_alpha = min(alpha_indexes), max(alpha_indexes)
    trailing_amounts = [
        (index, token, amount)
        for index, token in enumerate(tokens)
        if index > last_alpha
        for amount in [_standalone_money(token.get("text"))]
        if amount is not None
    ]
    if not trailing_amounts:
        return None

    amount_index, amount_token, total = trailing_amounts[-1]
    page_width = max(
        [float(token.get("page_width") or 0) for token in tokens] or [0.0]
    )
    # Receipt amounts occupy the right half. This rejects reference numbers
    # placed next to metadata labels in the header.
    if page_width and float(amount_token.get("cx") or 0) < page_width * 0.52:
        return None

    leading_tokens = tokens[:first_alpha]
    qty = 1.0
    qty_detected = False
    if leading_tokens:
        leading_text = normalize_text(
            " ".join(str(token.get("text") or "") for token in leading_tokens)
        )
        qty_match = re.fullmatch(r"\s*(\d{1,4})(?:\s*[xX*@])?\s*", leading_text)
        if qty_match:
            candidate_qty = float(qty_match.group(1))
            if 0 < candidate_qty <= 10_000:
                qty = candidate_qty
                qty_detected = True

    description_tokens = tokens[first_alpha:amount_index]
    description = normalize_text(
        " ".join(str(token.get("text") or "") for token in description_tokens)
    ).strip(" .,:;_-*")
    description = re.sub(
        r"(?i)\s+(?:rp|idr|usd|eur|sgd|aud)\.?$", "", description
    ).strip()
    embedded_qty = re.match(
        r"^\s*(\d{1,4})\s*[xX*@]\s+(.+)$", description
    )
    if embedded_qty:
        candidate_qty = float(embedded_qty.group(1))
        if 0 < candidate_qty <= 10_000:
            qty = candidate_qty
            qty_detected = True
            description = embedded_qty.group(2).strip(" .,:;_-*")
    if len(description) < 2 or not re.search(r"[A-Za-z]{2}", description):
        return None
    if _non_item_reason(line_key, description):
        return None

    monetary_before_total = [
        amount
        for index, _token, amount in trailing_amounts[:-1]
        if index > last_alpha
    ]
    price = monetary_before_total[-1] if monetary_before_total else total / qty
    if price <= 0 or total <= 0:
        return None
    if monetary_before_total and abs(qty * price - total) > max(1.0, total * 0.01):
        # When two amounts do not satisfy the row equation, the rightmost
        # value remains the strongest line-total evidence.
        price = total / qty

    description_confidence = _confidence(description_tokens)
    amount_confidence = float(amount_token.get("confidence") or 0)
    qty_confidence = (
        _confidence(leading_tokens)
        if qty_detected
        else min(description_confidence, amount_confidence) * 0.90
    )
    confidence = min(description_confidence, amount_confidence)
    return {
        "item": description,
        "description": description,
        "qty": qty,
        "unit": "",
        "price": round(price, 2),
        "total": round(total, 2),
        "amount_x": float(amount_token.get("cx") or 0),
        "line_y": float(line.get("cy") or 0),
        "confidence": {
            "item": description_confidence,
            "qty": qty_confidence,
            "unit": 0.0,
            "price": amount_confidence * (0.96 if not monetary_before_total else 1.0),
            "total": amount_confidence,
        },
        "validation": {
            "valid": abs(qty * price - total) <= max(1.0, total * 0.005),
            "expected_subtotal": round(qty * price, 2),
            "difference": round(abs(qty * price - total), 2),
            "fallback_heuristic": True,
            "receipt_row": True,
            "inferred_fields": [] if qty_detected else ["jumlah"],
        },
    }


def _receipt_quantity_price(
    line: dict[str, Any],
) -> tuple[float, float, float] | None:
    """Read a detail row such as `2 x Rp 48.000` below a product name."""
    text = normalize_text(str(line.get("text") or "")).replace("×", "x")
    match = re.fullmatch(
        r"\s*(\d{1,4}(?:[.,]\d+)?)\s*[xX*@]\s*"
        r"(?:(?:rp|idr)\.?\s*)?(\d[\d.,]*)\s*",
        text,
        re.I,
    )
    if not match:
        return None
    qty = _number(match.group(1))
    price = _money(match.group(2))
    if qty <= 0 or price <= 0 or qty > 10_000:
        return None
    return qty, price, float(line.get("confidence") or 0)


def _receipt_quantity_price_total(
    line: dict[str, Any],
) -> dict[str, Any] | None:
    """Read `qty x price` rows even when the line total is on the same OCR line."""
    text = normalize_text(str(line.get("text") or "")).replace("Ã—", "x")
    match = re.match(
        r"^\s*(\d{1,4}(?:[.,]\d+)?)\s*[xX*@]\s*"
        r"(?:(?:rp|idr)\.?\s*)?(\d[\d.,]*)\b(?P<rest>.*)$",
        text,
        re.I,
    )
    if not match:
        return None

    qty = _number(match.group(1))
    price = _money(match.group(2))
    if qty <= 0 or price <= 0 or qty > 10_000:
        return None

    trailing_amounts = [
        amount
        for amount in re.findall(
            r"(?i)(?:rp|idr)?\.?\s*\d[\d.,]*(?:,-)?",
            match.group("rest") or "",
        )
        if _money(amount) > 0
    ]
    total = _money(trailing_amounts[-1]) if trailing_amounts else qty * price
    if trailing_amounts and abs(qty * price - total) > max(1.0, total * 0.01):
        return None

    tokens = line.get("tokens") or []
    amount_token = next(
        (
            token
            for token in reversed(tokens)
            if _standalone_money(token.get("text")) is not None
        ),
        tokens[-1] if tokens else {},
    )
    return {
        "qty": qty,
        "price": round(price, 2),
        "total": round(float(total), 2),
        "confidence": float(line.get("confidence") or 0),
        "total_confidence": float(
            amount_token.get("confidence") or line.get("confidence") or 0
        ),
        "amount_x": float(amount_token.get("cx") or 0),
    }


def _receipt_detail_values(line: dict[str, Any]) -> dict[str, Any] | None:
    """Read `qty x price` and an optional right-aligned line total."""
    inline = _receipt_quantity_price_total(line)
    if inline:
        return inline

    tokens = sorted(line.get("tokens") or [], key=lambda token: token["x0"])
    if not tokens:
        return None
    for end in range(1, len(tokens) + 1):
        prefix_text = " ".join(
            str(token.get("text") or "") for token in tokens[:end]
        )
        detail = _receipt_quantity_price(
            {**line, "text": prefix_text, "tokens": tokens[:end]}
        )
        if detail is None:
            continue
        qty, price, confidence = detail
        trailing = [
            (token, amount)
            for token in tokens[end:]
            for amount in [_standalone_money(token.get("text"))]
            if amount is not None
        ]
        total_token, total = trailing[-1] if trailing else (tokens[end - 1], qty * price)
        if trailing and abs(qty * price - total) > max(1.0, total * 0.01):
            continue
        return {
            "qty": qty,
            "price": round(price, 2),
            "total": round(float(total), 2),
            "confidence": confidence,
            "total_confidence": float(total_token.get("confidence") or confidence),
            "amount_x": float(total_token.get("cx") or 0),
        }
    return None


def _receipt_description_only(line: dict[str, Any]) -> tuple[str, float] | None:
    """Accept a product-name row only when a numeric detail row follows it."""
    text = normalize_text(str(line.get("text") or "")).strip(" .,:;_-*")
    key = _key(text)
    if (
        len(text) < 2
        or not re.search(r"[A-Za-z]{2}", text)
        or _looks_like_document_date(text)
        or _non_item_reason(key, text)
        or any(
            key == prefix or key.startswith(f"{prefix} ")
            for prefix in _RECEIPT_METADATA_PREFIXES
        )
        or any(
            _standalone_money(token.get("text")) is not None
            for token in line.get("tokens") or []
        )
    ):
        return None
    return text, float(line.get("confidence") or 0)


def _extract_name_then_detail_receipt_items(
    lines: list[dict[str, Any]],
) -> list[dict[str, Any]]:
    """Pair a product-name row with `qty x price [line total]` below it."""
    selected: list[dict[str, Any]] = []
    for index in range(1, len(lines)):
        detail = _receipt_detail_values(lines[index])
        description = _receipt_description_only(lines[index - 1])
        if detail is None or description is None:
            continue
        name, name_confidence = description
        page_height = max(
            [
                float(token.get("page_height") or 0)
                for token in lines[index].get("tokens") or []
            ]
            or [0.0]
        )
        gap = float(lines[index].get("cy") or 0) - float(
            lines[index - 1].get("cy") or 0
        )
        if gap <= 0 or gap > max(100.0, page_height * 0.065):
            continue
        selected.append(
            {
                "item": name,
                "description": name,
                "qty": detail["qty"],
                "unit": "",
                "price": detail["price"],
                "total": detail["total"],
                "amount_x": detail["amount_x"],
                "line_y": float(lines[index].get("cy") or 0),
                "confidence": {
                    "item": name_confidence,
                    "qty": detail["confidence"],
                    "unit": 0.0,
                    "price": detail["confidence"],
                    "total": detail["total_confidence"],
                },
                "validation": {
                    "valid": True,
                    "expected_subtotal": round(
                        detail["qty"] * detail["price"], 2
                    ),
                    "difference": round(
                        abs(
                            detail["qty"] * detail["price"]
                            - detail["total"]
                        ),
                        2,
                    ),
                    "fallback_heuristic": True,
                    "receipt_row": True,
                    "receipt_name_then_detail_row": True,
                    "inferred_fields": [],
                },
            }
        )
    return selected


def _extract_two_line_receipt_items(
    lines: list[dict[str, Any]],
) -> list[dict[str, Any]]:
    """Pair a bold product/line-total row with its following `qty x price` row."""
    selected: list[dict[str, Any]] = []
    for index in range(1, len(lines)):
        detail = _receipt_quantity_price(lines[index])
        if detail is None:
            continue
        item = _receipt_line_item(lines[index - 1])
        if item is None:
            continue
        qty, price, detail_confidence = detail
        total = float(item["total"])
        difference = abs(qty * price - total)
        if difference > max(1.0, total * 0.01):
            continue
        page_height = max(
            [
                float(token.get("page_height") or 0)
                for token in lines[index].get("tokens") or []
            ]
            or [0.0]
        )
        gap = float(lines[index].get("cy") or 0) - float(
            lines[index - 1].get("cy") or 0
        )
        if gap <= 0 or gap > max(100.0, page_height * 0.065):
            continue
        item["qty"] = qty
        item["price"] = round(price, 2)
        item["line_y"] = float(lines[index].get("cy") or item["line_y"])
        item["confidence"]["qty"] = detail_confidence
        item["confidence"]["price"] = detail_confidence
        item["validation"].update(
            {
                "valid": True,
                "expected_subtotal": round(qty * price, 2),
                "difference": round(difference, 2),
                "receipt_two_line_row": True,
                "inferred_fields": [],
            }
        )
        selected.append(item)
    return selected


def _receipt_footer_evidence(
    lines: list[dict[str, Any]], items: list[dict[str, Any]]
) -> dict[str, Any] | None:
    """Find a printed footer amount that equals the sum of extracted rows."""
    if not items:
        return None
    target = round(sum(float(item["total"]) for item in items), 2)
    last_item_y = max(float(item.get("line_y") or 0) for item in items)
    candidates: list[dict[str, Any]] = []
    for line in lines:
        if float(line.get("cy") or 0) <= last_item_y:
            continue
        for token in line.get("tokens") or []:
            amount = _standalone_money(token.get("text"))
            if amount is None:
                continue
            if abs(float(amount) - target) <= max(1.0, target * 0.005):
                candidates.append(
                    {
                        "numeric_value": target,
                        "raw_text": str(token.get("text") or ""),
                        "confidence": _confidence_fraction(
                            token.get("confidence")
                        ),
                        "source": "receipt_footer_matches_item_totals",
                    }
                )
    return max(candidates, key=lambda item: item["confidence"]) if candidates else None


def _validate_receipt_items(
    lines: list[dict[str, Any]], selected: list[dict[str, Any]]
) -> list[dict[str, Any]]:
    if not selected:
        return []
    amount_positions = [float(item.pop("amount_x")) for item in selected]
    page_width = max(
        [
            float(token.get("page_width") or 0)
            for line in lines
            for token in line.get("tokens") or []
        ]
        or [1.0]
    )
    if max(amount_positions) - min(amount_positions) > page_width * 0.12:
        return []

    footer = _receipt_footer_evidence(lines, selected)
    if len(selected) < 2 and footer is None:
        return []
    target = float(footer["numeric_value"]) if footer else 0.0
    for item in selected:
        item.pop("line_y", None)
        item["validation"]["receipt_layout_validated"] = footer is not None
        item["validation"]["summary_target"] = target
        if footer:
            item["validation"]["summary_evidence"] = footer
    return selected


def _extract_headerless_receipt_items(
    lines: list[dict[str, Any]],
) -> list[dict[str, Any]]:
    """Run generic receipt strategies and select the strongest validated block."""
    candidate_groups: list[list[dict[str, Any]]] = []
    name_detail_items = _extract_name_then_detail_receipt_items(lines)
    if name_detail_items:
        validated = _validate_receipt_items(lines, name_detail_items)
        if validated:
            candidate_groups.append(validated)

    paired_items = _extract_two_line_receipt_items(lines)
    if paired_items:
        validated = _validate_receipt_items(lines, paired_items)
        if validated:
            candidate_groups.append(validated)

    summary_index = next(
        (
            index
            for index, line in enumerate(lines)
            if _summary_key_at_start(line.get("text", ""))
            in {"subtotal", "grand_total"}
        ),
        -1,
    )
    if summary_index > 0:
        selected: list[dict[str, Any]] = []
        previous_y = float(lines[summary_index].get("cy") or 0)
        for line in reversed(lines[:summary_index]):
            candidate = _receipt_line_item(line)
            line_y = float(line.get("cy") or 0)
            page_height = max(
                [
                    float(token.get("page_height") or 0)
                    for token in line.get("tokens") or []
                ]
                or [0.0]
            )
            maximum_gap = max(90.0, page_height * 0.075)
            if candidate and previous_y - line_y <= maximum_gap:
                selected.append(candidate)
                previous_y = line_y
                continue
            if selected:
                break

        selected.reverse()
        if selected:
            summary_target = _receipt_summary_target(lines, summary_index)
            item_total = round(
                sum(float(item["total"]) for item in selected), 2
            )
            matches_summary = bool(
                summary_target
                and abs(item_total - summary_target)
                <= max(1.0, summary_target * 0.01)
            )
            for item in selected:
                item["validation"][
                    "receipt_layout_validated"
                ] = matches_summary
                item["validation"]["summary_target"] = summary_target
            validated = _validate_receipt_items(lines, selected)
            if validated:
                candidate_groups.append(validated)

    def group_score(group: list[dict[str, Any]]) -> tuple[int, int, int, float]:
        footer_validated = int(
            all(
                bool(
                    (item.get("validation") or {}).get(
                        "receipt_layout_validated"
                    )
                )
                for item in group
            )
        )
        valid_rows = sum(
            bool((item.get("validation") or {}).get("valid"))
            for item in group
        )
        average_confidence = sum(
            float((item.get("confidence") or {}).get("item") or 0)
            for item in group
        ) / max(len(group), 1)
        return footer_validated, valid_rows, len(group), average_confidence

    return max(candidate_groups, key=group_score) if candidate_groups else []


def _fallback_items(lines: list[dict[str, Any]]) -> list[dict[str, Any]]:
    receipt_items = _extract_headerless_receipt_items(lines)
    if receipt_items:
        return receipt_items

    items = []
    for line in lines:
        line_key = _key(line["text"])
        if any(
            _match_alias(line["text"], FIELD_ALIASES[field])
            for field in (
                "invoice_number", "invoice_date", "due_date",
                "grand_total", "tax", "discount",
            )
        ) or re.match(
            r"^(?:invoice|faktur|nota|tanggal|date|kepada|customer|pelanggan|"
            r"vendor|supplier|alamat|address|telp|telepon|phone|email|"
            r"pembayaran|payment|rekening|account)\b",
            line_key,
        ) or (
            _looks_like_document_date(line["text"])
            or re.match(
                r"^\s*\d{1,2}:\d{2}(?::\d{2})?\b",
                normalize_text(line["text"]),
            )
            or any(
                line_key == prefix or line_key.startswith(f"{prefix} ")
                for prefix in _RECEIPT_METADATA_PREFIXES
            )
        ):
            continue
        numbers = re.findall(r"(?:Rp\.?\s*)?\d[\d.,]*", line["text"], re.I)
        amounts = [_money(number) for number in numbers]
        amounts = [amount for amount in amounts if amount > 0]
        if len(amounts) < 2:
            continue
        description = re.sub(r"(?:Rp\.?\s*)?\d[\d.,]*", " ", line["text"], flags=re.I)
        description = re.sub(r"\s+", " ", description).strip(" :-")
        if len(description) < 2 or _non_item_reason(line_key, description):
            continue
        qty = amounts[0] if len(amounts) >= 3 and amounts[0] < 1000 else 1.0
        price = amounts[-2] if len(amounts) >= 2 else amounts[-1]
        total = amounts[-1] if len(amounts) >= 2 else price * qty
        items.append({
            "item": description, "description": description, "qty": qty, "unit": "",
            "price": price, "total": total,
            "confidence": {name: line["confidence"] for name in ("item", "qty", "price", "total")},
            "validation": {
                "valid": abs(qty * price - total) <= max(1, total * 0.005),
                "expected_subtotal": round(qty * price, 2),
                "difference": round(abs(qty * price - total), 2),
                "fallback_heuristic": True,
                "inferred_fields": ["jumlah"] if len(amounts) < 3 else [],
            },
        })
    return items


def _extract_amount(lines: list[dict[str, Any]], field: str) -> tuple[float, float]:
    aliases = FIELD_ALIASES[field]
    for line in reversed(lines):
        label_ratio = max(
            [
                SequenceMatcher(None, _key(token["text"]), alias_word).ratio()
                for token in line["tokens"] for alias in aliases for alias_word in alias.split()
                if not re.search(r"\d", token["text"]) and _key(token["text"])
            ] or [0.0]
        )
        if not _match_alias(line["text"], aliases) and label_ratio < 0.72:
            continue
        number_tokens = [token for token in line["tokens"] if re.search(r"\d", token["text"])]
        for token in reversed(number_tokens):
            amount = _money(token["text"])
            if amount:
                return amount, float(token["confidence"])
    return 0.0, 0.0


def _fallback_document_date(
    lines: list[dict[str, Any]],
) -> tuple[str, float]:
    """Recover a printed receipt date even when it has no `Date` label."""
    date_pattern = re.compile(
        r"\b(?:\d{1,2}[/.-]\d{1,2}[/.-]\d{2,4}|"
        r"\d{4}[/.-]\d{1,2}[/.-]\d{1,2}|"
        r"\d{1,2}[\s-]+[A-Za-z]{3,12}[\s-]+\d{2,4})\b"
    )
    for line in lines:
        match = date_pattern.search(str(line.get("text") or ""))
        if match:
            return match.group(0), float(line.get("confidence") or 0)
    return "", 0.0


def parse_invoice(tokens: list[dict[str, Any]], raw_text: str, filename: str = "") -> dict[str, Any]:
    lines = _group_lines(tokens)
    table_header_index, _ = _find_table(lines)
    vendor, vendor_conf = _extract_vendor(lines)
    invoice_number, invoice_conf = _extract_labeled(lines, "invoice_number")
    if not invoice_number:
        invoice_number, invoice_conf = _fallback_invoice_identifier(lines)
    invoice_date, date_conf = _extract_labeled(lines, "invoice_date")
    if not invoice_date:
        invoice_date, date_conf = _fallback_document_date(lines)
    due_date, due_conf = _extract_labeled(lines, "due_date")
    currency, currency_conf = _detect_currency(raw_text)
    items = _extract_items(lines)
    tax, tax_conf = _extract_amount(lines, "tax")
    discount, discount_conf = _extract_amount(lines, "discount")
    grand_total, total_conf = _extract_amount(lines, "grand_total")
    category, category_conf = _infer_category(vendor, items, raw_text)
    subtotal_sum = round(sum(float(item["total"]) for item in items), 2)
    receipt_summary_evidence = next(
        (
            (item.get("validation") or {}).get("summary_evidence")
            for item in items
            if (item.get("validation") or {}).get("summary_evidence")
        ),
        None,
    )
    if receipt_summary_evidence:
        grand_total = float(
            receipt_summary_evidence.get("numeric_value") or grand_total
        )
        total_conf = float(receipt_summary_evidence.get("confidence") or 0) * 100
    calculated_total = round(subtotal_sum + tax - discount, 2)
    if grand_total <= 0:
        grand_total = calculated_total
        total_conf = min(
            [value for item in items for value in item["confidence"].values() if value > 0] or [0]
        )
    difference = round(abs(grand_total - calculated_total), 2)
    tolerance = max(1.0, grand_total * 0.005)
    invalid_rows = [index + 1 for index, item in enumerate(items) if not item["validation"]["valid"]]

    return {
        "vendor": vendor,
        "supplier": vendor,
        "company": vendor,
        "invoice_number": invoice_number.strip(" :#-") or "-",
        "invoice_date": _date(invoice_date),
        "due_date": _date(due_date),
        "currency": currency,
        "tax": tax,
        "discount": discount,
        "grand_total": grand_total,
        "receipt_summary_evidence": receipt_summary_evidence,
        "items": items,
        "document_fields": _dynamic_fields(lines, table_header_index),
        "tables": build_borderless_dynamic_tables(tokens),
        "confidence": {
            "vendor": vendor_conf, "invoice_number": invoice_conf, "invoice_date": date_conf,
            "due_date": due_conf, "currency": currency_conf, "tax": tax_conf,
            "discount": discount_conf, "grand_total": total_conf,
            "category": category_conf,
        },
        "validation": {
            "valid": difference <= tolerance and not invalid_rows and bool(items),
            "items_count": len(items),
            "invalid_rows": invalid_rows,
            "items_subtotal": subtotal_sum,
            "calculated_total": calculated_total,
            "ocr_grand_total": grand_total,
            "difference": difference,
            "formula": "sum(subtotal) + tax - discount",
            "warnings": (
                ([] if items else ["Tidak ada baris item yang terdeteksi."])
                + ([f"Periksa perhitungan pada baris: {', '.join(map(str, invalid_rows))}."] if invalid_rows else [])
                + (["Grand Total OCR berbeda dari hasil hitung sistem."] if difference > tolerance else [])
            ),
        },
        "activity_name": "OCR Processed Document",
        "division": "General",
        "category": category,
        "payment_method": "",
        "description": f"Invoice OCR: {filename}",
    }


def _confidence_fraction(value: Any) -> float:
    confidence = float(value or 0)
    if confidence > 1:
        confidence /= 100
    return round(max(0.0, min(1.0, confidence)), 4)


def normalize_numeric_field(
    raw_text: Any, confidence: Any = 0, *, monetary: bool = True
) -> dict[str, Any]:
    """Normalize OCR digits only inside a known numeric field and keep the audit value."""
    raw = normalize_text(str(raw_text or "")).strip()
    contextual = raw
    if re.search(r"\d", contextual) or re.match(
        r"(?i)^\s*(?:rp|idr|usd|eur|sgd|aud|\$)", contextual
    ):
        contextual = contextual.translate(
            str.maketrans(
                {
                    "O": "0",
                    "o": "0",
                    "I": "1",
                    "l": "1",
                    "ı": "1",
                    "S": "5",
                    "s": "5",
                    "B": "8",
                }
            )
        )
    detected = bool(re.search(r"\d", contextual))
    numeric_value = _money(contextual) if monetary else _number(contextual)
    if monetary and numeric_value:
        integer, fraction = divmod(round(numeric_value * 100), 100)
        normalized = f"Rp{integer:,.0f}".replace(",", ".")
        if fraction:
            normalized += f",{fraction:02d}"
    elif numeric_value:
        normalized = (
            str(int(numeric_value))
            if float(numeric_value).is_integer()
            else str(numeric_value).replace(".", ",")
        )
    else:
        normalized = raw
    return {
        "raw_text": raw,
        "normalized_text": normalized,
        "numeric_value": numeric_value,
        "confidence": _confidence_fraction(confidence),
        # A blank/unreadable OCR cell and a genuine printed zero must not be
        # treated as the same evidence. Keep this explicit throughout the
        # contract while retaining numeric_value for backwards compatibility.
        "detected": detected,
    }


def _split_quantity_unit(value: Any) -> tuple[float, str]:
    """Read cells such as `1 Rim`, `2 Pcs`, or `150 lembar`."""
    text = normalize_text(str(value or "")).strip()
    match = re.match(
        r"^\s*(\d{1,6}(?:[.,]\d+)?)\s*([A-Za-z][A-Za-z./-]{0,24})?\b",
        text,
    )
    if not match:
        return 0.0, ""
    qty = _number(match.group(1))
    unit = normalize_text(match.group(2) or "").strip(" .,:;-")
    return (qty if qty > 0 else 0.0), unit


def resolve_table_roles(table: dict[str, Any]) -> dict[str, int]:
    """Resolve ambiguous column names using the full left-to-right header context."""
    columns = table.get("columns") or []
    entries = [
        {
            "index": int(column.get("column_index", index)),
            "name": _key(column.get("name", "")),
            "role": column.get("semantic_role")
            or semantic_role(column.get("name", "")),
        }
        for index, column in enumerate(columns)
    ]
    roles: dict[str, int] = {}
    for entry in entries:
        if entry["role"] and entry["role"] not in roles:
            roles[entry["role"]] = entry["index"]

    # Some engines promote the first data row to a header, leaving column
    # names such as "UI/UX Design" and "85,000". Infer the stable structure
    # from all remaining rows instead of requiring a known header template.
    if "description" not in roles and entries:
        content_by_column: dict[int, list[str]] = {
            entry["index"]: [] for entry in entries
        }
        for row in table.get("rows") or []:
            for fallback_index, cell in enumerate(row.get("cells") or []):
                column_index = int(cell.get("column_index", fallback_index))
                text = str(cell.get("text") or "").strip()
                if text:
                    content_by_column.setdefault(column_index, []).append(text)
        description_scores = []
        for entry in entries:
            samples = [entry["name"], *content_by_column.get(entry["index"], [])]
            alpha = sum(len(re.findall(r"[A-Za-z]", sample)) for sample in samples)
            digits = sum(len(re.findall(r"\d", sample)) for sample in samples)
            long_text = sum(len(sample) >= 8 for sample in samples)
            description_scores.append(
                (alpha * 2.0 + long_text * 5.0 - digits * 0.8, entry["index"])
            )
        best_description = max(description_scores)
        if best_description[0] >= 8:
            roles["description"] = best_description[1]

        description_index = roles.get("description")
        numeric_columns = []
        if description_index is not None:
            for entry in entries:
                if entry["index"] <= description_index:
                    continue
                samples = [entry["name"], *content_by_column.get(entry["index"], [])]
                numeric_count = sum(bool(re.search(r"\d", sample)) for sample in samples)
                if numeric_count:
                    numeric_columns.append(entry["index"])
        if len(numeric_columns) >= 2:
            roles.setdefault("subtotal", numeric_columns[-1])
            roles.setdefault("price", numeric_columns[-2])
            qty_candidates = [
                index
                for index in numeric_columns[:-2]
                if index > roles.get("description", -1)
            ]
            if qty_candidates:
                roles.setdefault("qty", qty_candidates[0])

    # "Jumlah" means quantity in many Indonesian invoices, but it means line
    # amount when Kuantitas/Qty already exists and Jumlah is the rightmost
    # monetary column. Resolve this from neighboring columns instead of a
    # document-specific template.
    explicit_qty = next(
        (
            entry
            for entry in entries
            if re.search(r"\b(?:qty|quantity|kuantitas|jml)\b", entry["name"])
        ),
        None,
    )
    amount_candidates = [
        entry
        for entry in entries
        if entry["role"] == "subtotal" or re.search(
            r"\b(?:total|amount|subtotal|sub total|jumlah harga|total harga|jumlah)\b",
            entry["name"],
        )
    ]
    if explicit_qty is not None:
        roles["qty"] = explicit_qty["index"]
    if amount_candidates:
        price_index = roles.get("price", -1)
        right_amounts = [
            entry
            for entry in amount_candidates
            if entry["index"] != roles.get("qty")
            and entry["index"] > price_index
        ]
        if right_amounts:
            # Prefer the rightmost explicit amount column. This also replaces
            # a fuzzy/incorrect early match such as Lokasi -> subtotal.
            roles["subtotal"] = max(
                right_amounts, key=lambda entry: entry["index"]
            )["index"]
    return roles


def _table_role_map(table: dict[str, Any]) -> dict[str, int]:
    return resolve_table_roles(table)


def _cell_at_column(row: dict[str, Any], column_index: int | None) -> dict[str, Any]:
    if column_index is None:
        return {"text": "", "confidence": 0.0}
    for fallback_index, cell in enumerate(row.get("cells") or []):
        start = int(cell.get("column_index", fallback_index))
        span = max(1, int(cell.get("colspan", 1)))
        if start <= column_index < start + span:
            return cell
    return {"text": "", "confidence": 0.0}


def _numeric_options(
    cell: dict[str, Any], *, monetary: bool
) -> list[dict[str, Any]]:
    candidates = [
        {
            "text": cell.get("text", ""),
            "confidence": cell.get("confidence", 0),
            "engine": "selected",
        },
        *(cell.get("ocr_candidates") or []),
    ]
    if monetary:
        # A layout-only table can place Discount/Tax/Total inside one broad
        # structural cell. Keep each printed monetary fragment as an OCR
        # candidate so row arithmetic can select the actual line amount.
        fragment_pattern = re.compile(
            r"(?i)(?:rp|idr|usd|eur|sgd|aud|\$)?\s*-?\s*\d[\d.,]*"
        )
        fragments: list[dict[str, Any]] = []
        for candidate in candidates:
            candidate_text = str(candidate.get("text") or "")
            all_matches = list(fragment_pattern.finditer(candidate_text))
            matches = [
                match
                for match in all_matches
                if candidate_text[match.end() : match.end() + 1] != "%"
            ]
            if len(all_matches) <= 1:
                continue
            for match in matches:
                fragments.append(
                    {
                        "text": match.group(0).strip(),
                        "confidence": min(
                            100.0, float(candidate.get("confidence") or 0) + 0.5
                        ),
                        "engine": candidate.get("engine", "cell_fragment"),
                    }
                )
        candidates.extend(fragments)
    unique: dict[tuple[str, float], dict[str, Any]] = {}
    for candidate in candidates:
        audit = normalize_numeric_field(
            candidate.get("text"),
            candidate.get("confidence"),
            monetary=monetary,
        )
        if audit["numeric_value"] <= 0:
            continue
        candidate_audits = [audit]
        if monetary and 0 < audit["numeric_value"] < 1_000:
            scaled_audit = normalize_numeric_field(
                audit["numeric_value"] * 1_000,
                audit["confidence"] * 0.86,
                monetary=True,
            )
            scaled_audit.update(
                {
                    "raw_text": audit["raw_text"],
                    "source": "small_money_candidate_scaled_to_thousands",
                    "derived": True,
                }
            )
            candidate_audits.append(scaled_audit)
        for candidate_audit in candidate_audits:
            identity = (candidate_audit["raw_text"], candidate_audit["numeric_value"])
            candidate_audit["engine"] = candidate.get("engine", "paddleocr_retry")
            existing = unique.get(identity)
            if existing is None or candidate_audit["confidence"] > existing["confidence"]:
                unique[identity] = candidate_audit
    options = sorted(
        unique.values(),
        key=lambda option: option["confidence"],
        reverse=True,
    )
    return options or [
        normalize_numeric_field(
            cell.get("text"), cell.get("confidence"), monetary=monetary
        )
    ]


def _normalize_product_description(value: Any) -> tuple[str, dict[str, Any] | None]:
    """Apply only high-evidence, context-specific OCR ambiguity corrections."""
    raw = normalize_text(str(value or "")).strip()
    normalized = re.sub(r"^\s*(\d{1,3})\s+\1\s+", "", raw).strip()

    # Pencil grades conventionally use a trailing letter (2B, 4B, ...). OCR
    # commonly merges the space and reads B as 8. Restrict this correction to
    # that product context; numbers elsewhere in item names remain untouched.
    grade_match = re.fullmatch(r"(?i)(pensil|pencil)\s*([0-9])8", raw)
    if grade_match:
        normalized = f"{grade_match.group(1)} {grade_match.group(2)}B"

    if normalized == raw:
        return raw, None
    return normalized, {
        "raw_text": raw,
        "normalized_text": normalized,
        "reason": (
            "duplicate_row_number_removed"
            if re.match(r"^\s*(\d{1,3})\s+\1\s+", raw)
            else "contextual_ocr_ambiguity_B_8"
        ),
    }


SUMMARY_LABELS = {
    "subtotal": ("subtotal", "sub total"),
    "discount": ("discount", "diskon", "potongan"),
    "tax": ("tax", "ppn", "vat", "pajak"),
    "grand_total": (
        "grand total",
        "total keseluruhan",
        "total pembayaran",
        "total bayar",
        "amount due",
        "net total",
        "jumlah",
        "total",
    ),
}
NON_ITEM_PREFIXES = (
    "pembayaran",
    "payment",
    "pelunasan",
    "transfer",
    "rekening",
    "account",
    "metode pembayaran",
    "payment method",
    "konfirmasi pembayaran",
    "payment confirmation",
    "pembayaran diterima",
    "payment received",
    "sisa tagihan",
    "balance due",
    "amount paid",
    "no surat jalan",
    "nomor surat jalan",
    "terbilang",
    "catatan",
    "notes",
    "pesan",
    "message",
    "hormat kami",
    "regards",
    "tanda tangan",
    "signature",
    "manager",
    "finance",
)
NON_ITEM_PHRASES = (
    "nomor rekening",
    "no rekening",
    "nama bank",
    "atas nama",
    "silahkan transfer",
    "silakan transfer",
    "thank you",
    "terima kasih",
    "special notes and instructions",
)


def _non_item_reason(row_text: Any, description: Any = "") -> str | None:
    row_key = _key(str(row_text or ""))
    description_key = _key(str(description or ""))
    if not row_key:
        return "empty"
    if _summary_key(description_key) or re.match(
        r"^(?:sub\s*total|subtotal|discount|diskon|potongan|tax|pajak|ppn|vat|"
        r"grand\s+total|total(?:\s+(?:keseluruhan|pembayaran|bayar))?)\b",
        row_key,
    ):
        return "payment_summary"
    payment_stage_item = bool(
        re.search(
            r"\b(?:tahap|termin|progress|pekerjaan|jasa|instalasi|pemasangan)\b",
            description_key,
        )
    )
    if not payment_stage_item and any(
        description_key == prefix
        or description_key.startswith(f"{prefix} ")
        or row_key.startswith(f"{prefix} ")
        for prefix in NON_ITEM_PREFIXES
    ):
        return "footer_or_payment"
    if any(phrase in row_key for phrase in NON_ITEM_PHRASES):
        return "footer_or_payment"
    return None


def _summary_key(text: str) -> str | None:
    key = _key(text)
    # PP-Structure and the standalone text pass can produce duplicate words on
    # the same visual line.  Match labels anywhere in that line while keeping
    # the generic word "total" from swallowing "sub total".
    for name in ("subtotal", "discount", "tax"):
        if any(
            re.search(rf"(?:^|\s){re.escape(_key(alias))}(?:\s|$)", key)
            for alias in SUMMARY_LABELS[name]
        ):
            return name
    for alias in SUMMARY_LABELS["grand_total"]:
        alias_key = _key(alias)
        if alias_key == "total":
            if key == "total" or key.startswith("total "):
                return "grand_total"
        elif re.search(rf"(?:^|\s){re.escape(alias_key)}(?:\s|$)", key):
            return "grand_total"
    return None


def _summary_key_at_start(text: Any) -> str | None:
    words = _key(str(text or "")).split()
    collapsed = [
        word
        for index, word in enumerate(words)
        if index == 0 or word != words[index - 1]
    ]
    key = " ".join(collapsed)
    for name in ("subtotal", "discount", "tax", "grand_total"):
        for alias in sorted(SUMMARY_LABELS[name], key=len, reverse=True):
            alias_key = _key(alias)
            if key == alias_key or key.startswith(f"{alias_key} "):
                return name
    return None


def _summary_label_specificity(name: str, label: Any) -> int:
    """Prefer an explicit Grand Total label over the ambiguous word Total."""
    key = _key(str(label or ""))
    if name != "grand_total":
        return 2
    return 1 if key == "total" or key.startswith("total ") else 3


def _summary_values_from_text(
    text: Any, confidence: Any
) -> dict[str, dict[str, Any]]:
    """Pair every summary label with the amount following that label."""
    rendered = normalize_text(str(text or ""))
    label_matches: list[tuple[int, int, int, str]] = []
    for name in ("subtotal", "discount", "tax", "grand_total"):
        for alias in SUMMARY_LABELS[name]:
            words = [re.escape(word) for word in normalize_text(alias).split()]
            if not words:
                continue
            pattern = r"(?i)\b" + r"[\s:._-]*".join(words) + r"\b"
            for match in re.finditer(pattern, rendered):
                label_matches.append(
                    (match.start(), match.end(), match.end() - match.start(), name)
                )

    selected_labels: list[tuple[int, int, int, str]] = []
    for candidate in sorted(label_matches, key=lambda item: (item[0], -item[2])):
        if any(
            candidate[0] < existing[1] and existing[0] < candidate[1]
            for existing in selected_labels
        ):
            continue
        selected_labels.append(candidate)
    selected_labels.sort(key=lambda item: item[0])

    found: dict[str, dict[str, Any]] = {}
    amount_pattern = re.compile(
        r"(?i)(?:rp|idr|usd|eur|sgd|aud)?[.\s]*-?\s*\d[\d.,]*(?:\s*,-)?"
    )
    for index, (_, label_end, _, name) in enumerate(selected_labels):
        segment_end = (
            selected_labels[index + 1][0]
            if index + 1 < len(selected_labels)
            else len(rendered)
        )
        segment = rendered[label_end:segment_end]
        candidates = []
        for match in amount_pattern.finditer(segment):
            raw_amount = match.group(0).strip()
            suffix = segment[match.end() : match.end() + 1]
            if suffix == "%":
                continue
            audit = normalize_numeric_field(
                raw_amount, confidence, monetary=True
            )
            if audit["detected"]:
                candidates.append(
                    (
                        bool(re.match(r"(?i)^(?:rp|idr|usd|eur|sgd|aud)", raw_amount)),
                        audit,
                    )
                )
        if not candidates:
            continue
        currency_candidates = [audit for has_currency, audit in candidates if has_currency]
        selected = currency_candidates[0] if currency_candidates else candidates[-1][1]
        selected["source"] = "table_text_label_pair"
        existing = found.get(name)
        if existing is None or selected["confidence"] > existing["confidence"]:
            found[name] = selected
    return found


def _summary_from_tables(tables: list[dict[str, Any]]) -> dict[str, dict[str, Any]]:
    found: dict[str, dict[str, Any]] = {}
    for table in tables:
        for row in table.get("rows") or []:
            cells = row.get("cells") or []
            row_text = " ".join(
                str(cell.get("text") or "").strip()
                for cell in cells
                if str(cell.get("text") or "").strip()
            )
            row_confidence = max(
                [float(cell.get("confidence") or 0) for cell in cells] or [0]
            )
            paired_values = (
                _summary_values_from_text(row_text, row_confidence)
                if _summary_key_at_start(row_text)
                else {}
            )
            if paired_values:
                for paired_key, selected in paired_values.items():
                    selected["label_specificity"] = _summary_label_specificity(
                        paired_key, row_text
                    )
                    existing = found.get(paired_key)
                    if (
                        existing is None
                        or selected["label_specificity"]
                        > existing.get("label_specificity", 0)
                        or selected["label_specificity"]
                        == existing.get("label_specificity", 0)
                        and selected["confidence"]
                        > existing.get("confidence", 0)
                    ):
                        found[paired_key] = selected
                continue
            first_label = next(
                (
                    str(cell.get("text") or "").strip()
                    for cell in cells
                    if str(cell.get("text") or "").strip()
                ),
                "",
            )
            key = _summary_key_at_start(first_label)
            if not key:
                continue
            numeric_cells = [
                cell for cell in cells if re.search(r"\d", str(cell.get("text") or ""))
            ]
            if numeric_cells:
                cell = numeric_cells[-1]
                options = _numeric_options(cell, monetary=True)
                selected = max(options, key=lambda option: option["confidence"])
                selected["ocr_candidates"] = options
                selected["label_specificity"] = _summary_label_specificity(
                    key, first_label
                )
                existing = found.get(key)
                if (
                    existing is None
                    or selected["label_specificity"]
                    > existing.get("label_specificity", 0)
                    or selected["label_specificity"]
                    == existing.get("label_specificity", 0)
                    and selected["confidence"] > existing.get("confidence", 0)
                ):
                    found[key] = selected
    return found


def _inside_table(token: dict[str, Any], tables: list[dict[str, Any]]) -> bool:
    for table in tables:
        bbox = table.get("bbox")
        if not bbox or int(table.get("page", 1)) != int(token.get("page", 1)):
            continue
        if isinstance(bbox, dict):
            x1, y1, x2, y2 = bbox["x0"], bbox["y0"], bbox["x1"], bbox["y1"]
        else:
            x1, y1, x2, y2 = bbox
        if x1 <= token["cx"] <= x2 and y1 <= token["cy"] <= y2:
            return True
    return False


def _summary_from_tokens(
    tokens: list[dict[str, Any]], tables: list[dict[str, Any]]
) -> dict[str, dict[str, Any]]:
    found: dict[str, dict[str, Any]] = {}
    physical_tables = [
        table
        for table in tables
        if table.get("detection_method") == "grid"
    ]
    outside = [
        token
        for token in tokens
        if not _inside_table(token, physical_tables)
    ]
    # Summary rows are often physically inside the same ruled rectangle as the
    # item grid and are handled by _summary_from_tables. Page-token matching is
    # limited to positions outside the physical item grid so a PAJAK/DISKON
    # column header cannot be mistaken for the payment summary.
    for line in _group_lines(outside):
        line_tokens = line["tokens"]
        matched = None
        for start in range(len(line_tokens)):
            for length in (1, 2, 3):
                label_tokens = line_tokens[start : start + length]
                if len(label_tokens) != length:
                    continue
                label_text = " ".join(token["text"] for token in label_tokens)
                key = _summary_key_at_start(label_text)
                if key:
                    matched = (key, start, label_tokens)
                    break
            if matched:
                break
        if not matched:
            continue
        key, start, label_tokens = matched
        label_right = max(token["x1"] for token in label_tokens)
        numeric_tokens = [
            token
            for token in line_tokens[start:]
            if re.search(r"\d", token["text"])
            and token["x1"] >= label_right
        ]
        if not numeric_tokens:
            continue
        token = max(numeric_tokens, key=lambda candidate: candidate["x1"])
        candidate = normalize_numeric_field(
            token["text"], token["confidence"], monetary=True
        )
        label_text = " ".join(value["text"] for value in label_tokens)
        candidate["label_specificity"] = _summary_label_specificity(
            key, label_text
        )
        existing = found.get(key)
        if (
            candidate["numeric_value"] > 0
            and (
                existing is None
                or candidate["label_specificity"]
                > existing.get("label_specificity", 0)
                or candidate["label_specificity"]
                == existing.get("label_specificity", 0)
                and candidate["confidence"] > existing["confidence"]
            )
        ):
            found[key] = candidate
    return found


def _packed_numeric_audits(
    row: dict[str, Any], roles: dict[str, int]
) -> tuple[dict[str, Any], dict[str, Any], dict[str, Any]] | None:
    """Recover qty/price/total when a structure engine merged columns.

    Some OCR table engines return a good description column but combine all
    numeric columns into one cell.  Select values using the invoice invariant
    qty x unit price = line total, independent of their extracted text order.
    """
    description_index = roles.get("description")
    fragments: list[tuple[str, float]] = []
    amount_pattern = re.compile(
        r"(?i)(?:rp|idr|usd|eur|sgd|aud|\$)?\s*-?\s*"
        r"(?:\d[\d.,odqcsz]*|[zsoidqcb][\d.,odqcsz]{2,})"
    )
    for fallback_index, cell in enumerate(row.get("cells") or []):
        column_index = int(cell.get("column_index", fallback_index))
        if column_index == description_index:
            continue
        text = str(cell.get("text") or "")
        confidence = float(cell.get("confidence") or 0)
        for match in amount_pattern.finditer(text):
            raw = match.group(0).strip()
            if text[match.end() : match.end() + 1] == "%":
                continue
            audit = normalize_numeric_field(raw, confidence, monetary=True)
            if audit.get("detected"):
                fragments.append((raw, confidence))
    if not fragments:
        return None

    values = [
        normalize_numeric_field(raw, confidence, monetary=True)["numeric_value"]
        for raw, confidence in fragments
    ]

    # Keep explicit free/zero-valued rows as detected evidence. Printed zero
    # must not be treated as an unread cell.
    if (
        (len(values) >= 3 and values[0] > 0 and values[1] == 0 and values[2] == 0)
        or (len(values) == 2 and values[0] > 0 and values[1] == 0)
    ):
        price_fragment = fragments[1]
        total_fragment = fragments[2] if len(fragments) >= 3 else fragments[1]
        return (
            normalize_numeric_field(*fragments[0], monetary=False),
            normalize_numeric_field(*price_fragment, monetary=True),
            normalize_numeric_field(*total_fragment, monetary=True),
        )
    best: tuple[float, int, int, int] | None = None
    if len(values) >= 3:
        for qty_index, price_index, total_index in permutations(range(len(values)), 3):
            qty, price, total = (
                values[qty_index], values[price_index], values[total_index]
            )
            if qty <= 0 or price <= 0 or total <= 0 or qty > 1_000_000:
                continue
            difference = abs(qty * price - total)
            tolerance = max(1.0, total * 0.005)
            if difference > tolerance:
                continue
            score = 100.0 - difference / max(total, 1.0) * 100
            if abs(qty - round(qty)) <= 0.005:
                score += 12
            if qty <= 10_000:
                score += 5
            if total_index == len(values) - 1:
                score += 3
            candidate = (score, qty_index, price_index, total_index)
            if best is None or candidate[0] > best[0]:
                best = candidate

    if best is not None:
        _, qty_index, price_index, total_index = best
        selected = [fragments[qty_index], fragments[price_index], fragments[total_index]]
        return (
            normalize_numeric_field(*selected[0], monetary=False),
            normalize_numeric_field(*selected[1], monetary=True),
            normalize_numeric_field(*selected[2], monetary=True),
        )

    # Headerless/merged tables often retain only unit price and line total.
    # Their ratio is the quantity, so all three values remain auditable.
    if len(fragments) == 2:
        first, second = values
        if first > 0 and second >= first:
            inferred_qty = second / first
            if inferred_qty <= 10_000 and abs(inferred_qty - round(inferred_qty)) <= 0.005:
                first_raw, first_confidence = fragments[0]
                second_raw, second_confidence = fragments[1]
                qty = normalize_numeric_field(
                    round(inferred_qty),
                    min(first_confidence, second_confidence) * 0.9,
                    monetary=False,
                )
                qty.update(
                    {
                        "raw_text": "",
                        "derived": True,
                        "source": "total_divided_by_packed_price",
                    }
                )
                return (
                    qty,
                    normalize_numeric_field(first_raw, first_confidence, monetary=True),
                    normalize_numeric_field(second_raw, second_confidence, monetary=True),
                )

    # A description+amount table is still a valid invoice table. Preserve the
    # printed amount and derive qty=1 and unit price from that direct evidence.
    if len(fragments) == 1:
        raw, confidence = fragments[0]
        total = normalize_numeric_field(raw, confidence, monetary=True)
        qty = normalize_numeric_field(1, confidence * 0.9, monetary=False)
        price = normalize_numeric_field(raw, confidence * 0.9, monetary=True)
        for audit, source in (
            (qty, "single_amount_default_qty"),
            (price, "single_amount_as_unit_price"),
        ):
            audit.update({"raw_text": "", "derived": True, "source": source})
        return qty, price, total
    return None


def _table_mapping_quality(
    table: dict[str, Any],
    roles: dict[str, int],
    expected_totals: list[float] | None = None,
) -> tuple[tuple[float, int, int], dict[str, int]]:
    current_valid = 0
    swapped_valid = 0
    likely_items = 0
    line_total_sum = 0.0
    quality_rows, _ = _deduplicate_overlapping_rows(
        list(table.get("rows") or []), roles
    )
    for row in quality_rows:
        description_cell = _cell_at_column(row, roles.get("description"))
        description = str(description_cell.get("text") or "").strip()
        row_key = _key(
            " ".join(str(cell.get("text") or "") for cell in row.get("cells") or [])
        )
        if not description or _non_item_reason(row_key, description):
            continue
        qty = normalize_numeric_field(
            _cell_at_column(row, roles.get("qty")).get("text"), monetary=False
        )["numeric_value"]
        price = normalize_numeric_field(
            _cell_at_column(row, roles.get("price")).get("text"), monetary=True
        )["numeric_value"]
        total = normalize_numeric_field(
            _cell_at_column(row, roles.get("subtotal")).get("text"), monetary=True
        )["numeric_value"]
        if not any((qty, price, total)):
            packed = _packed_numeric_audits(row, roles)
            if packed:
                qty, price, total = (
                    float(audit.get("numeric_value") or 0) for audit in packed
                )
        if not any((qty, price, total)):
            continue
        likely_items += 1
        line_total_sum += float(total or (qty * price))
        derived_valid = False
        if not qty and total > 0 and roles.get("price") is not None:
            price_cell = _cell_at_column(row, roles.get("price"))
            for price_option in _numeric_options(price_cell, monetary=True):
                candidate_price = float(price_option.get("numeric_value") or 0)
                if candidate_price <= 0:
                    continue
                inferred_qty = total / candidate_price
                if (
                    0 < inferred_qty <= 10_000
                    and abs(inferred_qty - round(inferred_qty)) <= 0.005
                ):
                    derived_valid = True
                    current_valid += 1
                    break
        if qty and price and total:
            if abs(qty * price - total) <= max(1, total * 0.005):
                current_valid += 1
            if abs(qty * total - price) <= max(1, price * 0.005):
                swapped_valid += 1

    adjusted = dict(roles)
    valid_count = current_valid
    if swapped_valid > current_valid and "price" in adjusted and "subtotal" in adjusted:
        adjusted["price"], adjusted["subtotal"] = (
            adjusted["subtotal"],
            adjusted["price"],
        )
        valid_count = swapped_valid
    # Prefer the table that preserves the full invoice semantics. A visible
    # ruled grid is helpful, but on handwritten invoices it can merge several
    # physical rows into one broad cell. A layout table with description,
    # quantity, unit price and line-total columns is better evidence even when
    # one OCR number needs review.
    complete_role_count = sum(
        1 for role in ("description", "qty", "price", "subtotal") if role in roles
    )
    semantic_bonus = complete_role_count * 14.0
    if {"description", "qty", "price", "subtotal"}.issubset(roles):
        semantic_bonus += 35.0
    method_bonus = (
        min(12.0, likely_items * 1.2)
        if table.get("detection_method") == "grid"
        else min(18.0, likely_items * 1.5)
    )
    alignment_score = 0.0
    positive_targets = [
        float(target)
        for target in (expected_totals or [])
        if float(target or 0) > 0
    ]
    if line_total_sum > 0 and positive_targets:
        relative_error = min(
            abs(line_total_sum - target) / max(abs(target), 1.0)
            for target in positive_targets
        )
        if relative_error <= 0.005:
            alignment_score = 200.0
        elif relative_error <= 0.03:
            alignment_score = 80.0
        else:
            alignment_score = -min(160.0, relative_error * 120.0)
    return (
        (
            valid_count * 20
            + likely_items * 4
            + semantic_bonus
            + method_bonus
            + alignment_score,
            valid_count,
            likely_items,
        ),
        adjusted,
    )


def _row_bbox(row: dict[str, Any]) -> list[float] | None:
    boxes = []
    for cell in row.get("cells") or []:
        bbox = cell.get("bbox")
        if isinstance(bbox, dict):
            boxes.append([bbox["x0"], bbox["y0"], bbox["x1"], bbox["y1"]])
        elif isinstance(bbox, (list, tuple)) and len(bbox) >= 4:
            boxes.append([float(value) for value in bbox[:4]])
    if not boxes:
        return None
    return [
        min(box[0] for box in boxes),
        min(box[1] for box in boxes),
        max(box[2] for box in boxes),
        max(box[3] for box in boxes),
    ]


def _source_row_quality(
    row: dict[str, Any], roles: dict[str, int]
) -> tuple[int, int, float]:
    numeric_values = [
        normalize_numeric_field(
            _cell_at_column(row, roles.get(role)).get("text"),
            monetary=role != "qty",
        )["numeric_value"]
        for role in ("qty", "price", "subtotal")
    ]
    qty, price, total = numeric_values
    arithmetic_valid = int(
        bool(qty and price and total)
        and abs(qty * price - total) <= max(1.0, total * 0.005)
    )
    populated = sum(bool(value) for value in numeric_values)
    confidences = [
        float(cell.get("confidence") or 0) for cell in row.get("cells") or []
    ]
    return arithmetic_valid, populated, sum(confidences) / max(len(confidences), 1)


def _deduplicate_overlapping_rows(
    rows: list[dict[str, Any]], roles: dict[str, int]
) -> tuple[list[dict[str, Any]], int]:
    """Collapse only OCR alternatives that occupy the same visual row."""
    selected: list[dict[str, Any]] = []
    removed = 0
    for row in rows:
        bbox = _row_bbox(row)
        description = _key(
            str(_cell_at_column(row, roles.get("description")).get("text") or "")
        )
        duplicate_index = None
        if bbox and description:
            for index, existing in enumerate(selected):
                existing_bbox = _row_bbox(existing)
                existing_description = _key(
                    str(
                        _cell_at_column(
                            existing, roles.get("description")
                        ).get("text")
                        or ""
                    )
                )
                if not existing_bbox or not existing_description:
                    continue
                intersection = max(
                    0.0,
                    min(bbox[3], existing_bbox[3])
                    - max(bbox[1], existing_bbox[1]),
                )
                minimum_height = max(
                    1.0,
                    min(
                        bbox[3] - bbox[1],
                        existing_bbox[3] - existing_bbox[1],
                    ),
                )
                same_visual_line = intersection / minimum_height >= 0.55
                same_description = (
                    SequenceMatcher(
                        None, description, existing_description
                    ).ratio()
                    >= 0.82
                )
                if same_visual_line and same_description:
                    duplicate_index = index
                    break
        if duplicate_index is None:
            selected.append(row)
            continue
        removed += 1
        if _source_row_quality(row, roles) > _source_row_quality(
            selected[duplicate_index], roles
        ):
            selected[duplicate_index] = row
    return selected, removed


def _canonical_items_from_tables(
    tables: list[dict[str, Any]],
    expected_totals: list[float] | None = None,
) -> tuple[list[dict[str, Any]], list[dict[str, Any]]]:
    candidates: list[
        tuple[tuple[float, int, int], dict[str, Any], dict[str, int]]
    ] = []
    for table in tables:
        roles = _table_role_map(table)
        has_numeric_rows = any(
            re.search(
                r"\d",
                " ".join(
                    str(cell.get("text") or "")
                    for cell in row.get("cells") or []
                ),
            )
            for row in table.get("rows") or []
        )
        if "description" in roles and has_numeric_rows:
            quality, adjusted_roles = _table_mapping_quality(
                table, roles, expected_totals
            )
            candidates.append((quality, table, adjusted_roles))
    if not candidates:
        return [], []

    _, table, roles = max(
        candidates,
        key=lambda item: item[0],
    )
    items: list[dict[str, Any]] = []
    review_cells: list[dict[str, Any]] = []
    source_rows, removed_overlaps = _deduplicate_overlapping_rows(
        list(table.get("rows") or []), roles
    )
    if removed_overlaps:
        table["overlapping_rows_removed"] = removed_overlaps
    for source_row_index, row in enumerate(source_rows):
        description_cell = _cell_at_column(row, roles.get("description"))
        unit_cell = _cell_at_column(row, roles.get("unit"))
        qty_cell = _cell_at_column(row, roles.get("qty"))
        price_cell = _cell_at_column(row, roles.get("price"))
        total_cell = _cell_at_column(row, roles.get("subtotal"))
        row_text = " ".join(
            str(cell.get("text") or "") for cell in row.get("cells") or []
        )
        row_key = _key(row_text)
        excluded_reason = _non_item_reason(
            row_key, description_cell.get("text") or ""
        )
        if excluded_reason:
            continue

        description, description_correction = _normalize_product_description(
            description_cell.get("text", "")
        )
        leading_row_number = re.match(r"^\s*(\d{1,4})[.)]?\s+(.+)$", description)
        expected_row_number = len(items) + 1
        if (
            leading_row_number
            and int(leading_row_number.group(1)) == expected_row_number
        ):
            original_description = description
            description = leading_row_number.group(2).strip()
            description_correction = {
                "raw_text": original_description,
                "normalized_text": description,
                "reason": "leading_table_row_number_removed",
            }
        unit = normalize_text(unit_cell.get("text", "")).strip()
        qty_options = _numeric_options(qty_cell, monetary=False)
        price_options = _numeric_options(price_cell, monetary=True)
        total_options = _numeric_options(total_cell, monetary=True)
        qty_audit, price_audit, total_audit = (
            qty_options[0],
            price_options[0],
            total_options[0],
        )
        derived_numeric_fields: list[str] = []
        qty_unit_value, qty_unit_label = _split_quantity_unit(qty_cell.get("text"))
        if qty_unit_value > 0 and qty_unit_label and not unit:
            unit = qty_unit_label
            qty_audit = normalize_numeric_field(
                qty_unit_value,
                qty_cell.get("confidence"),
                monetary=False,
            )
            qty_audit.update(
                {
                    "raw_text": normalize_text(str(qty_cell.get("text") or "")),
                    "source": "qty_unit_cell",
                }
            )
            qty_options = [qty_audit, *qty_options]

        detected_numeric_count = sum(
            bool(audit.get("detected") and audit.get("numeric_value", 0) > 0)
            for audit in (qty_audit, price_audit, total_audit)
        )
        missing_numeric_roles = sum(
            role not in roles for role in ("qty", "price", "subtotal")
        )
        packed_audits = (
            _packed_numeric_audits(row, roles)
            if detected_numeric_count < 2 and missing_numeric_roles >= 2
            else None
        )
        if packed_audits:
            qty_audit, price_audit, total_audit = packed_audits
            qty_options = [qty_audit]
            price_options = [price_audit]
            total_options = [total_audit]
            if qty_audit.get("derived"):
                derived_numeric_fields.append("jumlah")
            if price_audit.get("derived"):
                derived_numeric_fields.append("harga")

        def derived_audit(
            value: float, confidence: float, source: str, *, monetary: bool
        ) -> dict[str, Any]:
            audit = normalize_numeric_field(
                value, confidence * 0.90, monetary=monetary
            )
            audit.update(
                {
                    "raw_text": "",
                    "derived": True,
                    "source": source,
                    "engine": "validated_arithmetic",
                }
            )
            return audit

        inferred_qty_options: list[dict[str, Any]] = []
        for price_candidate in price_options:
            for total_candidate in total_options:
                price_value = float(price_candidate.get("numeric_value") or 0)
                total_value = float(total_candidate.get("numeric_value") or 0)
                if price_value <= 0 or total_value <= 0:
                    continue
                inferred_qty = total_value / price_value
                if (
                    inferred_qty > 0
                    and inferred_qty <= 1_000_000
                    and abs(inferred_qty - round(inferred_qty)) <= 0.005
                ):
                    inferred_qty_options.append(
                        derived_audit(
                            round(inferred_qty),
                            min(
                                price_candidate["confidence"],
                                total_candidate["confidence"],
                            ),
                            "total_divided_by_price_candidate",
                            monetary=False,
                        )
                    )
        if inferred_qty_options:
            qty_options = [*inferred_qty_options, *qty_options]

        # Recover exactly one unread numeric cell only when the other two OCR
        # values provide an unambiguous row equation. The value is explicitly
        # marked as derived; it is never presented as direct OCR evidence.
        if (
            packed_audits is None
            and
            roles.get("qty") is None
            and roles.get("price") is None
            and total_audit.get("detected")
            and total_audit["numeric_value"] > 0
        ):
            qty_audit = derived_audit(
                1,
                total_audit["confidence"],
                "single_amount_default_qty",
                monetary=False,
            )
            price_audit = derived_audit(
                total_audit["numeric_value"],
                total_audit["confidence"],
                "single_amount_as_unit_price",
                monetary=True,
            )
            qty_options = [qty_audit]
            price_options = [price_audit]
            if "jumlah" not in derived_numeric_fields:
                derived_numeric_fields.append("jumlah")
            if "harga" not in derived_numeric_fields:
                derived_numeric_fields.append("harga")
        elif (
            (
                not qty_audit.get("detected")
                or qty_audit["numeric_value"] <= 0
            )
            and price_audit.get("detected")
            and total_audit.get("detected")
            and price_audit["numeric_value"] > 0
        ):
            if inferred_qty_options:
                qty_audit = max(
                    inferred_qty_options,
                    key=lambda option: (
                        float(option.get("confidence") or 0),
                        -float(option.get("numeric_value") or 0),
                    ),
                )
                derived_numeric_fields.append("jumlah")
        elif (
            (
                not price_audit.get("detected")
                or price_audit["numeric_value"] <= 0
            )
            and qty_audit.get("detected")
            and total_audit.get("detected")
            and qty_audit["numeric_value"] > 0
        ):
            inferred_price = (
                total_audit["numeric_value"] / qty_audit["numeric_value"]
            )
            if inferred_price >= 0:
                price_audit = derived_audit(
                    inferred_price,
                    min(qty_audit["confidence"], total_audit["confidence"]),
                    "total_divided_by_qty",
                    monetary=True,
                )
                price_options = [price_audit, *price_options]
                derived_numeric_fields.append("harga")
        elif (
            (
                not total_audit.get("detected")
                or total_audit["numeric_value"] <= 0
            )
            and qty_audit.get("detected")
            and price_audit.get("detected")
            and qty_audit["numeric_value"] > 0
            and price_audit["numeric_value"] > 0
        ):
            total_audit = derived_audit(
                qty_audit["numeric_value"] * price_audit["numeric_value"],
                min(qty_audit["confidence"], price_audit["confidence"]),
                "qty_multiplied_by_price",
                monetary=True,
            )
            total_options = [total_audit, *total_options]
            derived_numeric_fields.append("total")
        original_identity = (
            normalize_numeric_field(
                qty_cell.get("text"), qty_cell.get("confidence"), monetary=False
            )["numeric_value"],
            normalize_numeric_field(
                price_cell.get("text"), price_cell.get("confidence"), monetary=True
            )["numeric_value"],
            normalize_numeric_field(
                total_cell.get("text"), total_cell.get("confidence"), monetary=True
            )["numeric_value"],
        )
        valid_combinations = []
        for qty_candidate in qty_options:
            for price_candidate in price_options:
                for total_candidate in total_options:
                    expected_candidate = (
                        qty_candidate["numeric_value"]
                        * price_candidate["numeric_value"]
                    )
                    difference_candidate = abs(
                        expected_candidate - total_candidate["numeric_value"]
                    )
                    if difference_candidate <= max(
                        1.0, total_candidate["numeric_value"] * 0.005
                    ):
                        valid_combinations.append(
                            (
                                qty_candidate["confidence"]
                                + price_candidate["confidence"]
                                + total_candidate["confidence"]
                                + (0.08 if qty_candidate["numeric_value"] <= 10_000 else 0)
                                + (
                                    0.06
                                    if qty_candidate["numeric_value"]
                                    <= price_candidate["numeric_value"]
                                    else 0
                                )
                                + (
                                    0.04
                                    if abs(
                                        qty_candidate["numeric_value"]
                                        - round(qty_candidate["numeric_value"])
                                    )
                                    <= 0.005
                                    else 0
                                ),
                                qty_candidate,
                                price_candidate,
                                total_candidate,
                            )
                        )
        if valid_combinations:
            _, qty_audit, price_audit, total_audit = max(
                valid_combinations, key=lambda combination: combination[0]
            )
        else:
            qty_value = float(qty_audit.get("numeric_value") or 0)
            price_value = float(price_audit.get("numeric_value") or 0)
            total_value = float(total_audit.get("numeric_value") or 0)
            expected_from_qty_price = round(qty_value * price_value, 2)
            if (
                qty_value > 0
                and price_value > 0
                and expected_from_qty_price > 0
                and (
                    not total_audit.get("detected")
                    or total_value <= 0
                    or (
                        total_value < expected_from_qty_price
                        and expected_from_qty_price >= 1_000
                        and total_value < 1_000
                    )
                    or abs(total_value * 1_000 - expected_from_qty_price)
                    <= max(1.0, expected_from_qty_price * 0.005)
                )
            ):
                total_audit = derived_audit(
                    expected_from_qty_price,
                    min(qty_audit["confidence"], price_audit["confidence"]),
                    "qty_multiplied_by_price_after_ocr_scale_check",
                    monetary=True,
                )
                total_options = [total_audit, *total_options]
                if "total" not in derived_numeric_fields:
                    derived_numeric_fields.append("total")
            elif (
                qty_value > 0
                and total_value > 0
                and price_value > 0
                and abs(expected_from_qty_price - total_value)
                > max(1.0, total_value * 0.005)
            ):
                expected_price = round(total_value / qty_value, 2)
                if (
                    expected_price > 0
                    and (
                        (
                            expected_price >= 1_000
                            and price_value < 1_000
                            and abs(price_value * 1_000 - expected_price)
                            <= max(1.0, expected_price * 0.005)
                        )
                        or (
                            qty_value == 1
                            and total_value >= 1_000
                            and price_value < total_value
                        )
                    )
                ):
                    price_audit = derived_audit(
                        expected_price,
                        min(qty_audit["confidence"], total_audit["confidence"]),
                        "total_divided_by_qty_after_ocr_scale_check",
                        monetary=True,
                    )
                    price_options = [price_audit, *price_options]
                    if "harga" not in derived_numeric_fields:
                        derived_numeric_fields.append("harga")
        selected_from_retry = original_identity != (
            qty_audit["numeric_value"],
            price_audit["numeric_value"],
            total_audit["numeric_value"],
        )
        if not description or not any(
            (qty_audit["numeric_value"], price_audit["numeric_value"], total_audit["numeric_value"])
        ):
            continue

        confidence = {
            "nama_barang": _confidence_fraction(description_cell.get("confidence")),
            "satuan": _confidence_fraction(unit_cell.get("confidence")),
            "jumlah": qty_audit["confidence"],
            "harga": price_audit["confidence"],
            "total": total_audit["confidence"],
        }
        numeric_presence = {
            "jumlah": bool(qty_audit.get("detected")),
            "harga": bool(price_audit.get("detected")),
            "total": bool(total_audit.get("detected")),
        }
        missing_numeric_fields = [
            field_name
            for field_name, is_present in numeric_presence.items()
            if not is_present
        ]
        expected = round(qty_audit["numeric_value"] * price_audit["numeric_value"], 2)
        difference = round(abs(expected - total_audit["numeric_value"]), 2)
        tolerance = max(1.0, total_audit["numeric_value"] * 0.005)
        row_number = len(items) + 1
        validation = {
            "is_valid": not missing_numeric_fields and difference <= tolerance,
            "ocr_qty": qty_audit["numeric_value"],
            "ocr_price": price_audit["numeric_value"],
            "ocr_total": total_audit["numeric_value"],
            "expected_total": expected,
            "difference": difference,
            "numeric_presence": numeric_presence,
            "missing_numeric_fields": missing_numeric_fields,
            "derived_numeric_fields": derived_numeric_fields,
            "source_row": source_row_index,
            "selected_from_retry": selected_from_retry,
            "ocr_candidates": {
                "jumlah": qty_options,
                "harga": price_options,
                "total": total_options,
            },
        }
        for field_name, field_confidence in confidence.items():
            if field_name == "satuan" and "unit" not in roles:
                continue
            if field_name in missing_numeric_fields:
                continue
            if field_confidence < 0.70:
                review_cells.append(
                    {
                        "row": row_number,
                        "field": field_name,
                        "reason": "low_confidence",
                        "confidence": field_confidence,
                    }
                )
        for field_name in missing_numeric_fields:
            review_cells.append(
                {
                    "row": row_number,
                    "field": field_name,
                    "reason": "nilai_ocr_tidak_terbaca",
                    "confidence": confidence[field_name],
                }
            )
        for field_name in derived_numeric_fields:
            review_cells.append(
                {
                    "row": row_number,
                    "field": field_name,
                    "reason": "diturunkan_dari_perhitungan_tervalidasi",
                    "confidence": confidence[field_name],
                }
            )
        if not missing_numeric_fields and not validation["is_valid"]:
            review_cells.append(
                {
                    "row": row_number,
                    "field": "perhitungan",
                    "reason": "jumlah_x_harga_tidak_sama_dengan_total",
                    "difference": difference,
                }
            )
        item = {
            "nama_barang": description,
            "satuan": unit or "-",
            "jumlah": qty_audit["numeric_value"],
            "jumlah_raw": qty_audit["raw_text"],
            "harga_raw": price_audit["raw_text"],
            "harga_normalized": price_audit["normalized_text"],
            "harga_value": price_audit["numeric_value"],
            "total_raw": total_audit["raw_text"],
            "total_normalized": total_audit["normalized_text"],
            "total_value": total_audit["numeric_value"],
            "confidence": confidence,
            "validation": validation,
            "numeric_presence": numeric_presence,
            "raw_cells": {
                "nama_barang": description_cell.get("original_text", description_cell.get("text", "")),
                "satuan": unit_cell.get("original_text", unit_cell.get("text", "")),
                "jumlah": qty_cell.get("original_text", qty_cell.get("text", "")),
                "harga": price_cell.get("original_text", price_cell.get("text", "")),
                "total": total_cell.get("original_text", total_cell.get("text", "")),
            },
            # Flat aliases keep persistence and existing report pages compatible.
            "item": description,
            "description": description,
            "unit": unit or "",
            "qty": qty_audit["numeric_value"],
            "price": price_audit["numeric_value"],
            "total": total_audit["numeric_value"],
        }
        if description_correction:
            item["description_correction"] = description_correction
        items.append(item)
    return items, review_cells


def _item_numeric_combinations(
    item: dict[str, Any],
) -> list[tuple[float, dict[str, Any], dict[str, Any], dict[str, Any]]]:
    candidates = (item.get("validation") or {}).get("ocr_candidates") or {}
    qty_options = candidates.get("jumlah") or []
    price_options = candidates.get("harga") or []
    total_options = candidates.get("total") or []
    valid: dict[
        tuple[float, float, float],
        tuple[float, dict[str, Any], dict[str, Any], dict[str, Any]],
    ] = {}
    for qty in qty_options:
        for price in price_options:
            for total in total_options:
                qty_value = float(qty.get("numeric_value") or 0)
                price_value = float(price.get("numeric_value") or 0)
                total_value = float(total.get("numeric_value") or 0)
                if not qty_value or not price_value or not total_value:
                    continue
                difference = abs(qty_value * price_value - total_value)
                if difference > max(1.0, total_value * 0.005):
                    continue
                identity = (qty_value, price_value, total_value)
                score = (
                    float(qty.get("confidence") or 0)
                    + float(price.get("confidence") or 0)
                    + float(total.get("confidence") or 0)
                )
                current = valid.get(identity)
                if current is None or score > current[0]:
                    valid[identity] = (score, qty, price, total)
    return sorted(valid.values(), key=lambda option: option[0], reverse=True)[:8]


def _apply_item_numeric_combination(
    item: dict[str, Any],
    combination: tuple[float, dict[str, Any], dict[str, Any], dict[str, Any]],
) -> None:
    _, qty, price, total = combination
    item.update(
        {
            "jumlah": qty["numeric_value"],
            "jumlah_raw": qty["raw_text"],
            "harga_raw": price["raw_text"],
            "harga_normalized": price["normalized_text"],
            "harga_value": price["numeric_value"],
            "total_raw": total["raw_text"],
            "total_normalized": total["normalized_text"],
            "total_value": total["numeric_value"],
            "qty": qty["numeric_value"],
            "price": price["numeric_value"],
            "total": total["numeric_value"],
        }
    )
    item["confidence"].update(
        {
            "jumlah": qty["confidence"],
            "harga": price["confidence"],
            "total": total["confidence"],
        }
    )
    expected = round(qty["numeric_value"] * price["numeric_value"], 2)
    difference = round(abs(expected - total["numeric_value"]), 2)
    item["validation"].update(
        {
            "is_valid": difference <= max(1.0, total["numeric_value"] * 0.005),
            "ocr_qty": qty["numeric_value"],
            "ocr_price": price["numeric_value"],
            "ocr_total": total["numeric_value"],
            "expected_total": expected,
            "difference": difference,
            "selected_from_retry": True,
            "selected_from_summary_reconciliation": True,
        }
    )


def _reconcile_items_with_summary(
    items: list[dict[str, Any]], targets: list[float]
) -> set[int]:
    """Use OCR alternatives to match a document total without inventing numbers."""
    if not items or not targets:
        return set()
    positive_targets = [float(target) for target in targets if float(target or 0) > 0]
    if not positive_targets:
        return set()
    current_total = round(sum(float(item["total_value"]) for item in items), 2)
    if any(
        abs(current_total - target) <= max(1.0, target * 0.005)
        for target in positive_targets
    ):
        return set()

    option_rows = [_item_numeric_combinations(item) for item in items]
    if any(not options for options in option_rows):
        return set()

    best_solution = None
    for target in positive_targets:
        target_cents = int(round(target * 100))
        states: dict[
            int,
            tuple[
                float,
                list[tuple[float, dict[str, Any], dict[str, Any], dict[str, Any]]],
            ],
        ] = {0: (0.0, [])}
        for options in option_rows:
            next_states = {}
            for subtotal_cents, (score, selected) in states.items():
                for option in options:
                    total_cents = int(round(option[3]["numeric_value"] * 100))
                    candidate_sum = subtotal_cents + total_cents
                    candidate = (score + option[0], [*selected, option])
                    existing = next_states.get(candidate_sum)
                    if existing is None or candidate[0] > existing[0]:
                        next_states[candidate_sum] = candidate
            if len(next_states) > 2500:
                ranked = sorted(
                    next_states.items(),
                    key=lambda state: (
                        abs(state[0] - target_cents),
                        -state[1][0],
                    ),
                )[:2500]
                next_states = dict(ranked)
            states = next_states
        tolerance_cents = max(100, int(round(target_cents * 0.005)))
        matches = [
            (abs(subtotal - target_cents), -score, selected)
            for subtotal, (score, selected) in states.items()
            if abs(subtotal - target_cents) <= tolerance_cents
        ]
        if matches:
            candidate_solution = min(matches, key=lambda match: (match[0], match[1]))
            if best_solution is None or candidate_solution[:2] < best_solution[:2]:
                best_solution = candidate_solution

    if best_solution is None:
        return set()
    changed_rows: set[int] = set()
    for index, (item, combination) in enumerate(zip(items, best_solution[2])):
        previous = (
            item["jumlah"],
            item["harga_value"],
            item["total_value"],
        )
        selected = (
            combination[1]["numeric_value"],
            combination[2]["numeric_value"],
            combination[3]["numeric_value"],
        )
        if previous != selected:
            _apply_item_numeric_combination(item, combination)
            changed_rows.add(index + 1)
    return changed_rows


def _canonical_items_from_positioned_fallback(
    extracted_items: list[dict[str, Any]],
) -> tuple[list[dict[str, Any]], list[dict[str, Any]]]:
    """Map conservative positioned-text rows when no table region survives."""
    items: list[dict[str, Any]] = []
    review_cells: list[dict[str, Any]] = []
    for source_index, extracted in enumerate(extracted_items):
        description, description_correction = _normalize_product_description(
            extracted.get("item") or extracted.get("description") or ""
        )
        qty = float(extracted.get("qty") or 0)
        price = float(extracted.get("price") or 0)
        total = float(extracted.get("total") or 0)
        if (
            not description
            or qty <= 0
            or price <= 0
            or total <= 0
            or _non_item_reason(description, description)
        ):
            continue
        expected = round(qty * price, 2)
        difference = round(abs(expected - total), 2)
        valid = difference <= max(1.0, abs(total) * 0.005)
        legacy_confidence = extracted.get("confidence") or {}
        confidence = {
            "nama_barang": _confidence_fraction(
                legacy_confidence.get("item")
                or legacy_confidence.get("description")
            ),
            "satuan": _confidence_fraction(legacy_confidence.get("unit")),
            "jumlah": _confidence_fraction(legacy_confidence.get("qty")),
            "harga": _confidence_fraction(legacy_confidence.get("price")),
            "total": _confidence_fraction(legacy_confidence.get("total")),
        }
        inferred_fields = list(
            dict.fromkeys(
                (extracted.get("validation") or {}).get("inferred_fields")
                or []
            )
        )
        receipt_layout_validated = bool(
            (extracted.get("validation") or {}).get(
                "receipt_layout_validated"
            )
        )
        row_number = len(items) + 1
        validation = {
            "is_valid": valid,
            "ocr_qty": qty,
            "ocr_price": price,
            "ocr_total": total,
            "expected_total": expected,
            "difference": difference,
            "numeric_presence": {
                "jumlah": True, "harga": True, "total": True
            },
            "missing_numeric_fields": [],
            "derived_numeric_fields": inferred_fields,
            "source_row": source_index,
            "selected_from_retry": False,
            "fallback_without_detected_table": True,
            "receipt_layout_validated": receipt_layout_validated,
            "summary_target": (
                (extracted.get("validation") or {}).get("summary_target")
            ),
        }
        item = {
            "nama_barang": description,
            "satuan": extracted.get("unit") or "-",
            "jumlah": qty,
            "jumlah_raw": str(qty),
            "harga_raw": str(price),
            "harga_normalized": normalize_numeric_field(
                price, monetary=True
            )["normalized_text"],
            "harga_value": price,
            "total_raw": str(total),
            "total_normalized": normalize_numeric_field(
                total, monetary=True
            )["normalized_text"],
            "total_value": total,
            "confidence": confidence,
            "validation": validation,
            "numeric_presence": validation["numeric_presence"],
            "raw_cells": {
                "nama_barang": description,
                "satuan": extracted.get("unit") or "",
                "jumlah": str(qty),
                "harga": str(price),
                "total": str(total),
            },
            "extraction_source": "positioned_text_fallback",
            "item": description,
            "description": description,
            "unit": extracted.get("unit") or "",
            "qty": qty,
            "price": price,
            "total": total,
        }
        if description_correction:
            item["description_correction"] = description_correction
        items.append(item)
        if not receipt_layout_validated:
            review_cells.append(
                {
                    "row": row_number,
                    "field": "baris",
                    "reason": "fallback_teks_berposisi_tanpa_region_tabel",
                    "confidence": min(
                        confidence["nama_barang"],
                        confidence["harga"],
                        confidence["total"],
                    ),
                }
            )
        for field_name in inferred_fields:
            if receipt_layout_validated:
                continue
            review_cells.append(
                {
                    "row": row_number,
                    "field": field_name,
                    "reason": "diturunkan_dari_perhitungan_tervalidasi",
                    "confidence": confidence.get(field_name, 0),
                }
            )
        if not valid:
            review_cells.append(
                {
                    "row": row_number,
                    "field": "perhitungan",
                    "reason": "jumlah_x_harga_tidak_sama_dengan_total",
                    "difference": difference,
                }
            )
    return items, review_cells


def _field_from_dynamic(
    fields: list[dict[str, Any]], *keys: str
) -> tuple[str, float]:
    for key in keys:
        field = next((entry for entry in fields if entry.get("key") == key), None)
        if field and str(field.get("value") or "").strip():
            return str(field["value"]).strip(), float(field.get("confidence") or 0)
    return "", 0.0


def _looks_like_document_date(value: Any) -> bool:
    return bool(
        re.search(
            r"\b(?:\d{1,2}[/.\-]\d{1,2}[/.\-]\d{2,4}|"
            r"\d{4}[/.\-]\d{1,2}[/.\-]\d{1,2}|"
            r"\d{1,2}[\s\-]+[A-Za-z]{3,12}[\s\-]+\d{2,4})\b",
            str(value or ""),
        )
    )


def build_invoice_contract(
    parsed: dict[str, Any],
    tables: list[dict[str, Any]],
    tokens: list[dict[str, Any]],
    raw_text: str,
) -> dict[str, Any]:
    """Map detected layout into the stable SmartDocs invoice contract."""
    fields = parsed.get("document_fields") or []
    # "Customer:" is frequently a section heading while "Nama:" holds its
    # actual value. Prefer the explicit name field when both were detected.
    customer, customer_conf = _field_from_dynamic(fields, "name", "customer")
    address, address_conf = _field_from_dynamic(fields, "address")
    address = re.sub(r"^(?:JI|J1)\s*\.", "Jl.", address, flags=re.I)
    if re.search(
        r"\b(?:ukuran|qty|quantity|kuantitas|harga|price|subtotal|total|ppn|pajak)\b|%",
        address,
        re.I,
    ):
        address, address_conf = "", 0
    city, city_conf = _field_from_dynamic(fields, "city")
    phone, phone_conf = _field_from_dynamic(fields, "phone")
    invoice_date_raw, invoice_date_conf = _field_from_dynamic(fields, "invoice_date")
    if not _looks_like_document_date(invoice_date_raw):
        invoice_date_raw = parsed.get("invoice_date", "")
        invoice_date_conf = (parsed.get("confidence") or {}).get("invoice_date", 0)
    if not _looks_like_document_date(invoice_date_raw):
        invoice_date_raw, invoice_date_conf = "", 0
    vendor_value = str(parsed.get("vendor") or "").strip()
    vendor_confidence = (parsed.get("confidence") or {}).get("vendor")
    if _looks_like_address(vendor_value):
        if not address:
            address, address_conf = vendor_value, vendor_confidence
        vendor_value, vendor_confidence = "", 0

    def field(value: Any, confidence: Any, optional: bool = True) -> dict[str, Any]:
        rendered = str(value or "").strip()
        return {
            "value": rendered if rendered else ("-" if optional else ""),
            "confidence": _confidence_fraction(confidence),
        }

    invoice = {
        "vendor": field(
            vendor_value,
            vendor_confidence,
        ),
        "customer": field(customer, customer_conf),
        "invoice_number": field(
            parsed.get("invoice_number"),
            (parsed.get("confidence") or {}).get("invoice_number"),
        ),
        "invoice_date": field(invoice_date_raw, invoice_date_conf),
        "address": field(address, address_conf),
        "city": field(city, city_conf),
        "phone": field(phone, phone_conf),
    }
    due_date, due_conf = _field_from_dynamic(fields, "due_date")
    if not _looks_like_document_date(due_date):
        due_date = parsed.get("due_date", "")
        due_conf = (parsed.get("confidence") or {}).get("due_date", 0)
    if _looks_like_document_date(due_date):
        invoice["due_date"] = field(due_date, due_conf)

    summary_details = _summary_from_tables(tables)
    token_summary = _summary_from_tokens(tokens, tables)
    dynamic_summary: dict[str, dict[str, Any]] = {}
    for key in ("subtotal", "discount", "tax", "grand_total"):
        raw_value, raw_confidence = _field_from_dynamic(fields, key)
        normalized = normalize_numeric_field(
            raw_value,
            raw_confidence,
            monetary=True,
        )
        if not normalized["detected"]:
            continue
        normalized["source"] = "document_field"
        dynamic_summary[key] = normalized

    summary_candidate_pool: dict[str, list[dict[str, Any]]] = {}
    for source in (summary_details, token_summary, dynamic_summary):
        for key, candidate in source.items():
            pool = summary_candidate_pool.setdefault(key, [])
            if not any(
                existing.get("numeric_value") == candidate.get("numeric_value")
                for existing in pool
            ):
                pool.append(candidate)
    for key, candidate in token_summary.items():
        existing = summary_details.get(key)
        if (
            existing is None
            or candidate.get("label_specificity", 0)
            > existing.get("label_specificity", 0)
            or candidate["numeric_value"] == existing.get("numeric_value")
            and candidate["confidence"] > existing.get("confidence", 0)
            or (
                candidate.get("label_specificity", 0)
                == existing.get("label_specificity", 0)
                and candidate["confidence"]
                >= existing.get("confidence", 0) + 0.12
            )
        ):
            summary_details[key] = candidate
    # Dynamic fields are produced from label/value pairs outside the detected
    # table. They are an important fallback for footer totals whose label is
    # slightly misread (for example "Total" becoming "Toval").
    for key, candidate in dynamic_summary.items():
        existing = summary_details.get(key)
        if existing is None or (
            float(existing.get("numeric_value") or 0) <= 0
            and float(candidate.get("numeric_value") or 0) > 0
        ):
            summary_details[key] = candidate
    summary = {
        "subtotal": summary_details.get("subtotal", {}).get("numeric_value"),
        "discount": summary_details.get("discount", {}).get("numeric_value"),
        "tax": summary_details.get("tax", {}).get("numeric_value"),
        "grand_total": summary_details.get("grand_total", {}).get("numeric_value"),
    }
    discount = float(summary["discount"] or 0)
    tax = float(summary["tax"] or 0)
    table_selection_targets = [
        float(value)
        for value in (
            summary["subtotal"],
            (
                float(summary["grand_total"]) + discount - tax
                if summary["grand_total"] is not None
                else None
            ),
            (
                float(summary["grand_total"]) + discount
                if summary["grand_total"] is not None
                else None
            ),
        )
        if value is not None and float(value) > 0
    ]
    items, review_cells = _canonical_items_from_tables(
        tables, table_selection_targets
    )
    fallback_rows_used = False
    if not items and parsed.get("items"):
        items, review_cells = _canonical_items_from_positioned_fallback(
            list(parsed.get("items") or [])
        )
        fallback_rows_used = bool(items)
    receipt_summary_evidence = parsed.get("receipt_summary_evidence") or {}
    receipt_total = float(
        receipt_summary_evidence.get("numeric_value") or 0
    )
    canonical_item_total = round(
        sum(float(item.get("total_value") or 0) for item in items), 2
    )
    if (
        receipt_total > 0
        and items
        and abs(canonical_item_total - receipt_total)
        <= max(1.0, receipt_total * 0.005)
    ):
        receipt_total_audit = normalize_numeric_field(
            receipt_total,
            receipt_summary_evidence.get("confidence"),
            monetary=True,
        )
        receipt_total_audit.update(
            {
                "raw_text": receipt_summary_evidence.get("raw_text", ""),
                "source": receipt_summary_evidence.get(
                    "source", "receipt_footer_matches_item_totals"
                ),
                "derived": False,
            }
        )
        summary_details["grand_total"] = dict(receipt_total_audit)
        summary["grand_total"] = receipt_total
        if summary["subtotal"] is None:
            subtotal_audit = dict(receipt_total_audit)
            subtotal_audit.update(
                {
                    "raw_text": "",
                    "source": "validated_receipt_item_totals",
                    "derived": True,
                }
            )
            summary_details["subtotal"] = subtotal_audit
            summary["subtotal"] = receipt_total
    # When table OCR joins several footer labels, a high-confidence but wrong
    # amount can beat the correctly positioned page-token reading. Select the
    # OCR tax alternative that satisfies the printed subtotal and grand total.
    if summary["subtotal"] is not None and summary["grand_total"] is not None:
        expected_tax = round(
            float(summary["grand_total"])
            - float(summary["subtotal"])
            + discount,
            2,
        )
        tax_matches = [
            candidate
            for candidate in summary_candidate_pool.get("tax", [])
            if abs(float(candidate.get("numeric_value") or 0) - expected_tax)
            <= max(1.0, abs(expected_tax) * 0.005)
        ]
        if tax_matches:
            selected_tax = max(
                tax_matches,
                key=lambda candidate: float(candidate.get("confidence") or 0),
            )
            summary_details["tax"] = selected_tax
            summary["tax"] = selected_tax["numeric_value"]
            tax = float(summary["tax"] or 0)
    item_total_evidence = round(
        sum(float(item["total_value"]) for item in items), 2
    )
    # A footer amount can be read with one wrong digit while all item rows and
    # the payable total still agree. In that case the arithmetic evidence is
    # stronger than the isolated subtotal OCR value.
    if summary["grand_total"] is not None and items:
        expected_item_subtotal = round(
            float(summary["grand_total"]) + discount - tax,
            2,
        )
        evidence_tolerance = max(
            1.0,
            abs(expected_item_subtotal) * 0.005,
        )
        subtotal_is_missing_or_conflicting = (
            summary["subtotal"] is None
            or abs(float(summary["subtotal"]) - item_total_evidence)
            > evidence_tolerance
        )
        if (
            expected_item_subtotal > 0
            and abs(item_total_evidence - expected_item_subtotal)
            <= evidence_tolerance
            and subtotal_is_missing_or_conflicting
        ):
            evidence_confidences = [
                float(
                    summary_details.get("grand_total", {}).get("confidence")
                    or 0
                ),
                *[
                    float((item.get("confidence") or {}).get("total") or 0)
                    for item in items
                ],
            ]
            reconciled_subtotal = normalize_numeric_field(
                item_total_evidence,
                min(evidence_confidences or [0]) * 0.95,
                monetary=True,
            )
            reconciled_subtotal.update(
                {
                    "raw_text": (
                        summary_details.get("subtotal", {}).get("raw_text")
                        or ""
                    ),
                    "source": "validated_item_totals_and_grand_total",
                    "derived": True,
                }
            )
            summary_details["subtotal"] = reconciled_subtotal
            summary["subtotal"] = item_total_evidence
    if summary["grand_total"] is None and summary["subtotal"] is not None and items:
        formula_evidence = round(
            float(summary["subtotal"]) - discount + tax,
            2,
        )
        evidence_tolerance = max(1.0, abs(formula_evidence) * 0.005)
        if abs(item_total_evidence - formula_evidence) <= evidence_tolerance:
            evidence_confidences = [
                float(summary_details["subtotal"].get("confidence") or 0),
                *[
                    float((item.get("confidence") or {}).get("total") or 0)
                    for item in items
                ],
            ]
            if summary["tax"] is not None:
                evidence_confidences.append(
                    float(summary_details["tax"].get("confidence") or 0)
                )
            if summary["discount"] is not None:
                evidence_confidences.append(
                    float(summary_details["discount"].get("confidence") or 0)
                )
            derived_confidence = max(
                0.0,
                min(evidence_confidences or [0]) * 0.95,
            )
            derived = normalize_numeric_field(
                formula_evidence,
                derived_confidence,
                monetary=True,
            )
            derived.update(
                {
                    "raw_text": "",
                    "source": "validated_summary_formula_and_item_totals",
                    "derived": True,
                }
            )
            summary_details["grand_total"] = derived
            summary["grand_total"] = formula_evidence
    reconciliation_targets = [
        float(value)
        for value in (
            summary["subtotal"],
            (
                float(summary["grand_total"]) + discount - tax
                if summary["grand_total"] is not None
                else None
            ),
            (
                float(summary["grand_total"]) + discount
                if summary["grand_total"] is not None
                else None
            ),
        )
        if value is not None and float(value) > 0
    ]
    reconciled_rows = _reconcile_items_with_summary(items, reconciliation_targets)
    if reconciled_rows:
        review_cells = [
            cell
            for cell in review_cells
            if not (
                cell.get("field") == "perhitungan"
                and int(cell.get("row") or 0) in reconciled_rows
            )
        ]

    item_subtotal = round(sum(item["total_value"] for item in items), 2)
    if summary["subtotal"] is None and summary["grand_total"] is not None:
        expected_subtotal = round(
            float(summary["grand_total"]) + discount - tax,
            2,
        )
        if (
            expected_subtotal > 0
            and abs(item_subtotal - expected_subtotal)
            <= max(1.0, expected_subtotal * 0.005)
        ):
            derived_subtotal = normalize_numeric_field(
                item_subtotal,
                min(
                    [
                        float(
                            summary_details.get("grand_total", {}).get(
                                "confidence"
                            )
                            or 0
                        ),
                        *[
                            float(
                                (item.get("confidence") or {}).get("total")
                                or 0
                            )
                            for item in items
                        ],
                    ]
                    or [0]
                )
                * 0.95,
                monetary=True,
            )
            derived_subtotal.update(
                {
                    "raw_text": "",
                    "source": "reconciled_item_totals_and_grand_total",
                    "derived": True,
                }
            )
            summary_details["subtotal"] = derived_subtotal
            summary["subtotal"] = item_subtotal
    standard_grand_total = round(item_subtotal - discount + tax, 2)
    tax_included_grand_total = round(item_subtotal - discount, 2)
    grand_total_value = (
        float(summary["grand_total"])
        if summary["grand_total"] is not None
        else None
    )
    grand_tolerance = max(
        1.0,
        abs(grand_total_value or standard_grand_total) * 0.005,
    )
    line_totals_include_tax = bool(
        tax > 0
        and grand_total_value is not None
        and abs(tax_included_grand_total - grand_total_value) <= grand_tolerance
        and abs(standard_grand_total - grand_total_value) > grand_tolerance
    )
    calculated_grand_total = (
        tax_included_grand_total
        if line_totals_include_tax
        else standard_grand_total
    )
    subtotal_comparison_value = (
        round(item_subtotal - tax, 2)
        if line_totals_include_tax
        else item_subtotal
    )
    subtotal_difference = (
        round(abs(float(summary["subtotal"]) - subtotal_comparison_value), 2)
        if summary["subtotal"] is not None
        else None
    )
    grand_total_difference = (
        round(abs(float(summary["grand_total"]) - calculated_grand_total), 2)
        if summary["grand_total"] is not None
        else None
    )
    tolerance = max(1.0, calculated_grand_total * 0.005)
    calculation_issue = (
        any(not item["validation"]["is_valid"] for item in items)
        or (
            subtotal_difference is not None
            and subtotal_difference
            > max(1.0, abs(subtotal_comparison_value) * 0.005)
        )
        or (grand_total_difference is not None and grand_total_difference > tolerance)
    )

    missing_critical = []
    if invoice["invoice_number"]["value"] in {"", "-", "None"}:
        missing_critical.append("invoice_number")
    if not items:
        missing_critical.append("items")
    if summary["grand_total"] is None:
        missing_critical.append("grand_total")
    important_low = [
        cell
        for cell in review_cells
        if cell.get("field") in {"jumlah", "harga", "total", "nama_barang"}
    ]
    too_many_low = len(important_low) > max(2, len(items))
    if missing_critical or calculation_issue or too_many_low:
        review_status = "Perlu ditinjau"
    elif review_cells:
        review_status = "Valid dengan catatan"
    else:
        review_status = "Valid"
    validation = {
        "is_valid": not missing_critical and not calculation_issue,
        "status": review_status,
        "review_cells": review_cells,
        "missing_critical": missing_critical,
        "item_count": len(items),
        "items_subtotal": item_subtotal,
        "subtotal_comparison_value": subtotal_comparison_value,
        "ocr_subtotal": summary["subtotal"],
        "subtotal_difference": subtotal_difference,
        "calculated_grand_total": calculated_grand_total,
        "ocr_grand_total": summary["grand_total"],
        "grand_total_difference": grand_total_difference,
        "formula_mode": (
            "line_totals_include_tax"
            if line_totals_include_tax
            else "subtotal_minus_discount_plus_tax"
        ),
        "numeric_rows_reconciled": sorted(reconciled_rows),
        "positioned_text_fallback_used": fallback_rows_used,
    }

    result = {
        **parsed,
        "invoice": invoice,
        "items": items,
        "summary": summary,
        "summary_details": summary_details,
        "validation": validation,
        "review_status": review_status,
        "raw_text": raw_text,
        "ocr_text": raw_text,
        "tables": tables,
        # Flat aliases keep database/report endpoints backward-compatible.
        "vendor": invoice["vendor"]["value"] if invoice["vendor"]["value"] != "-" else "",
        "invoice_number": (
            invoice["invoice_number"]["value"]
            if invoice["invoice_number"]["value"] != "-"
            else ""
        ),
        "invoice_date": (
            invoice["invoice_date"]["value"]
            if invoice["invoice_date"]["value"] != "-"
            else ""
        ),
        "due_date": invoice.get("due_date", {}).get("value", ""),
        "subtotal": float(summary["subtotal"] or item_subtotal or 0),
        "tax": float(summary["tax"] or 0),
        "discount": float(summary["discount"] or 0),
        "grand_total": float(summary["grand_total"] or 0),
        "confidence": {
            key: round(value["confidence"] * 100, 2)
            for key, value in invoice.items()
        },
    }
    return result
