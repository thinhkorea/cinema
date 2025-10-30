package com.example.cinema.service;

import com.example.cinema.domain.Booking;
import com.lowagie.text.*;
import com.lowagie.text.pdf.*;
import com.lowagie.text.pdf.draw.LineSeparator;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;

@Service
public class TicketPDFService {

    private static final String FONT_PATH = "fonts/Roboto-Regular.ttf";
    private static final String LOGO_PATH = "static/logo.png";
    private BaseFont baseFont;

    public TicketPDFService() {
        try {
            ClassPathResource fontRes = new ClassPathResource(FONT_PATH);
            this.baseFont = BaseFont.createFont(
                    fontRes.getPath(),
                    BaseFont.IDENTITY_H,
                    BaseFont.EMBEDDED,
                    true,
                    fontRes.getInputStream().readAllBytes(),
                    null);
        } catch (Exception e) {
            throw new RuntimeException("Không load được font Việt hoá: " + e.getMessage(), e);
        }
    }

    private Font f(float size, int style) {
        return new Font(baseFont, size, style, new java.awt.Color(33, 37, 41));
    }

    public byte[] generate(Booking b) {
        try {
            var baos = new ByteArrayOutputStream();

            //Khổ vé: A6 xoay ngang, margin nhỏ gọn
            var document = new Document(PageSize.A6.rotate(), 18, 18, 10, 10);
            PdfWriter.getInstance(document, baos);
            document.open();

            // =======================
            // HEADER: logo + tên rạp
            // =======================
            PdfPTable header = new PdfPTable(new float[] { 1f, 3f });
            header.setWidthPercentage(100);

            Image logoImg = null;
            try {
                var logoRes = new ClassPathResource(LOGO_PATH);
                if (logoRes.exists()) {
                    logoImg = Image.getInstance(logoRes.getInputStream().readAllBytes());
                    logoImg.scaleToFit(45, 45);
                }
            } catch (Exception ignore) {
            }

            PdfPCell logoCell = new PdfPCell();
            logoCell.setBorder(Rectangle.NO_BORDER);
            if (logoImg != null)
                logoCell.addElement(logoImg);
            header.addCell(logoCell);

            PdfPCell brandCell = new PdfPCell();
            brandCell.setBorder(Rectangle.NO_BORDER);
            brandCell.setPaddingTop(6f);
            Paragraph brand = new Paragraph("CINEMA MANAGEMENT", f(16, Font.BOLD));
            brand.setAlignment(Element.ALIGN_LEFT);
            Paragraph addr = new Paragraph("Vé xem phim / Movie Ticket", f(9, Font.NORMAL));
            addr.setAlignment(Element.ALIGN_LEFT);
            brandCell.addElement(brand);
            brandCell.addElement(addr);
            header.addCell(brandCell);

            document.add(header);
            document.add(new LineSeparator());

            // =======================
            // THÔNG TIN VÉ
            // =======================
            var showtime = b.getShowtime();
            var movie = showtime.getMovie();
            var room = showtime.getRoom();
            SimpleDateFormat dtFmt = new SimpleDateFormat("HH:mm dd/MM/yyyy");

            PdfPTable info = new PdfPTable(1);
            info.setWidthPercentage(100);
            info.setSpacingBefore(4f);
            info.setSpacingAfter(2f);

            info.addCell(cell("Phim / Movie: " + movie.getTitle(), 13, Font.BOLD));
            info.addCell(cell("Phòng / Room: " + room.getRoomName(), 11, Font.NORMAL));
            info.addCell(cell("Ghế / Seat: " + b.getSeat().getSeatNumber(), 11, Font.BOLD));
            info.addCell(
                    cell("Suất chiếu / Showtime: " + dtFmt.format(java.sql.Timestamp.valueOf(showtime.getStartTime())),
                            11, Font.NORMAL));
            info.addCell(cell("Giá / Price: " + money(showtime.getPrice()) + " VND", 11, Font.NORMAL));
            info.addCell(cell("Thanh toán / Payment: " + (b.getPaymentMethod() != null ? b.getPaymentMethod() : "-"),
                    11, Font.NORMAL));
            info.addCell(cell("Mã vé / Ticket ID: " + b.getBookingId(), 11, Font.NORMAL));
            if (b.getTxnRef() != null)
                info.addCell(cell("Mã giao dịch / TxnRef: " + b.getTxnRef(), 9, Font.ITALIC));

            document.add(info);

            // =======================
            // QR CODE
            // =======================
            String qrData = buildQrPayload(b);
            Image qr = qrImage(qrData, 100, 100); // giảm kích thước QR cho vừa trang
            qr.scaleToFit(100, 100);
            qr.setAlignment(Element.ALIGN_CENTER);
            document.add(qr);

            Paragraph qrTitle = new Paragraph("Quét QR khi vào rạp / Scan at gate", f(9, Font.ITALIC));
            qrTitle.setAlignment(Element.ALIGN_CENTER);
            qrTitle.setSpacingBefore(2f);
            document.add(qrTitle);

            // =======================
            // FOOTER (chú thích)
            // =======================
            document.add(Chunk.NEWLINE);
            Paragraph note = new Paragraph(
                    "Lưu ý: Có mặt trước giờ chiếu 10 phút. Vé không hoàn/từ chối sau khi xuất. " +
                            "Giữ sạch, không làm rách QR.\n— Cảm ơn bạn đã chọn rạp của chúng tôi —",
                    f(8.5f, Font.NORMAL));
            note.setAlignment(Element.ALIGN_CENTER);
            note.setSpacingBefore(3f);
            document.add(note);

            document.close();
            return baos.toByteArray();

        } catch (Exception e) {
            throw new RuntimeException("Lỗi tạo PDF: " + e.getMessage(), e);
        }
    }

    private PdfPCell cell(String text, float size, int style) {
        PdfPCell c = new PdfPCell(new Phrase(text, f(size, style)));
        c.setBorder(Rectangle.NO_BORDER);
        c.setPadding(1.5f);
        return c;
    }

    private String money(Double v) {
        return v == null ? "-" : String.format("%,.0f", v);
    }

    private String buildQrPayload(Booking b) {
        return "TICKET|" +
                "ID=" + b.getBookingId() +
                ";MOVIE=" + safe(b.getShowtime().getMovie().getTitle()) +
                ";ROOM=" + safe(b.getShowtime().getRoom().getRoomName()) +
                ";SEAT=" + b.getSeat().getSeatNumber() +
                ";TIME=" + b.getShowtime().getStartTime() +
                (b.getTxnRef() != null ? ";TXN=" + b.getTxnRef() : "");
    }

    private String safe(String s) {
        return s == null ? "" : s.replace(";", ",");
    }

    private Image qrImage(String data, int w, int h) throws Exception {
        var writer = new QRCodeWriter();
        var matrix = writer.encode(data, BarcodeFormat.QR_CODE, w, h);
        BufferedImage bi = MatrixToImageWriter.toBufferedImage(matrix);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        javax.imageio.ImageIO.write(bi, "png", out);
        return Image.getInstance(out.toByteArray());
    }
}
