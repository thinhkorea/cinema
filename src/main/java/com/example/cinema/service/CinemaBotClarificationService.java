package com.example.cinema.service;

import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

@Component
public class CinemaBotClarificationService {

    private final CinemaBotLexicon lexicon;

    public CinemaBotClarificationService(CinemaBotLexicon lexicon) {
        this.lexicon = lexicon;
    }

    public String resolveClarificationMessage(String userMessage) {
        String normalized = lexicon.normalize(userMessage);
        if (isVoucherReferenceWithoutCode(normalized)) {
            return "Bạn muốn hỏi hạn sử dụng của mã voucher nào ạ? Bạn vui lòng gửi mã voucher cụ thể, hoặc nếu bạn đang hỏi hạn dùng điểm thành viên thì mình có thể kiểm tra điểm giúp bạn.";
        }
        if (isUnclearReferenceQuestion(normalized)) {
            return "Bạn đang muốn hỏi về điểm thành viên, mã voucher, vé đã đặt hay suất chiếu nào ạ? Bạn nói rõ thêm một chút để mình kiểm tra đúng thông tin cho bạn nhé.";
        }
        return null;
    }

    public boolean isExpiryQuestion(String userMessage) {
        String normalized = lexicon.normalize(userMessage);
        return lexicon.containsAny(normalized,
                "hạn sử dụng",
                "hạn dùng",
                "hết hạn",
                "sử dụng đến khi nào",
                "dùng đến khi nào",
                "áp dụng đến khi nào");
    }

    public boolean hasReferenceTerm(String userMessage) {
        String normalized = lexicon.normalize(userMessage);
        return lexicon.containsWord(normalized, "đó")
                || lexicon.containsWord(normalized, "nó")
                || lexicon.contains(normalized, "cái đó")
                || lexicon.contains(normalized, "vừa rồi")
                || lexicon.contains(normalized, "ở trên");
    }

    private boolean isVoucherReferenceWithoutCode(String normalizedMessage) {
        return lexicon.contains(normalizedMessage, "voucher")
                && isExpiryQuestion(normalizedMessage)
                && hasReferenceTerm(normalizedMessage)
                && !hasVoucherCode(normalizedMessage);
    }

    private boolean isUnclearReferenceQuestion(String normalizedMessage) {
        return hasReferenceTerm(normalizedMessage)
                && isExpiryQuestion(normalizedMessage)
                && !lexicon.contains(normalizedMessage, "điểm")
                && !lexicon.contains(normalizedMessage, "voucher");
    }

    private boolean hasVoucherCode(String normalizedMessage) {
        java.util.regex.Matcher matcher = Pattern.compile("\\b[a-z0-9]{4,}\\b").matcher(normalizedMessage);
        while (matcher.find()) {
            String token = matcher.group();
            if (token.chars().anyMatch(Character::isDigit)) {
                return true;
            }
        }
        return false;
    }
}
