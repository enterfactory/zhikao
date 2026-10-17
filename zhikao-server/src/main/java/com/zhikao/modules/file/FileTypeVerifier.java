package com.zhikao.modules.file;

import com.zhikao.common.BusinessException;
import org.springframework.stereotype.Component;
import java.nio.charset.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.ZipFile;

/** MIME is derived from bytes and container contents, never from the client header. */
@Component
public class FileTypeVerifier {
    public String verify(byte[] bytes, String ext) {
        if(bytes==null || bytes.length==0) throw new BusinessException("文件内容为空");
        String mime = detect(bytes, ext);
        Set<String> expected = switch(ext) {
            case "png" -> Set.of("image/png");
            case "jpg","jpeg" -> Set.of("image/jpeg");
            case "gif" -> Set.of("image/gif");
            case "webp" -> Set.of("image/webp");
            case "bmp" -> Set.of("image/bmp");
            case "pdf" -> Set.of("application/pdf");
            case "docx" -> Set.of("application/vnd.openxmlformats-officedocument.wordprocessingml.document");
            case "xlsx" -> Set.of("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            case "doc","xls" -> Set.of("application/x-ole-storage");
            case "txt","csv" -> Set.of("text/plain;charset=UTF-8");
            default -> Set.of();
        };
        if(!expected.contains(mime)) throw new BusinessException("文件实际类型与扩展名不一致或不受支持，请上传真实的文档或图片");
        validatePayload(bytes,mime);
        return mime;
    }
    private void validatePayload(byte[] bytes,String mime) {
        try {
            if("application/pdf".equals(mime)) {
                try(var pdf=org.apache.pdfbox.Loader.loadPDF(bytes)){if(pdf.getNumberOfPages()<1)throw new IllegalArgumentException();}
            } else if(mime.startsWith("image/")&&!"image/webp".equals(mime)) {
                try(var stream=javax.imageio.ImageIO.createImageInputStream(new java.io.ByteArrayInputStream(bytes))){
                    var readers=javax.imageio.ImageIO.getImageReaders(stream);if(!readers.hasNext())throw new IllegalArgumentException();
                    var reader=readers.next();
                    try{reader.setInput(stream);int width=reader.getWidth(0),height=reader.getHeight(0);
                        if(width<1||height<1||(long)width*height>20000000||reader.read(0)==null)throw new IllegalArgumentException();
                    }finally{reader.dispose();}
                }
            } else if("image/webp".equals(mime)) {
                long size=Integer.toUnsignedLong(java.nio.ByteBuffer.wrap(bytes,4,4).order(java.nio.ByteOrder.LITTLE_ENDIAN).getInt());
                if(bytes.length<20||size+8!=bytes.length)throw new IllegalArgumentException();
            }
        }catch(Exception invalid){throw new BusinessException("文件内容损坏、受密码保护或图片尺寸过大，请检查后重新上传");}
    }
    private String detect(byte[] b,String ext) {
        if(starts(b,0x89,0x50,0x4e,0x47,13,10,26,10)) return "image/png";
        if(starts(b,255,216,255)) return "image/jpeg";
        if(starts(b,71,73,70,56)) return "image/gif";
        if(b.length>=12 && new String(b,0,4,StandardCharsets.US_ASCII).equals("RIFF")
                && new String(b,8,4,StandardCharsets.US_ASCII).equals("WEBP")) return "image/webp";
        if(starts(b,66,77)) return "image/bmp";
        if(starts(b,37,80,68,70,45)) return "application/pdf";
        if(starts(b,0xd0,0xcf,0x11,0xe0,0xa1,0xb1,0x1a,0xe1)) return "application/x-ole-storage";
        if(starts(b,80,75,3,4)) return officeType(b);
        if(Set.of("txt","csv").contains(ext)) {
            try {
                String text=StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                        .decode(java.nio.ByteBuffer.wrap(b)).toString();
                if(text.indexOf('\0')>=0 || text.toLowerCase(Locale.ROOT).matches("(?s).*<(?:script|html|svg)\\b.*")) return "unsupported";
                return "text/plain;charset=UTF-8";
            } catch(CharacterCodingException e) { return "unsupported"; }
        }
        return "unsupported";
    }
    private String officeType(byte[] b) {
        Path temp=null;
        try {
            temp=Files.createTempFile("zhikao-type-",".zip");Files.write(temp,b);
            try(ZipFile zip=new ZipFile(temp.toFile())) {
                if(zip.size()>20000 || zip.getEntry("[Content_Types].xml")==null) return "unsupported";
                long expanded=0;
                var entries=zip.entries();
                while(entries.hasMoreElements()) {
                    var entry=entries.nextElement(); expanded+=Math.max(0,entry.getSize());
                    if(expanded>128L*1024*1024 || entry.getName().toLowerCase(Locale.ROOT).endsWith("vbaproject.bin")) return "unsupported";
                }
                if(zip.getEntry("word/document.xml")!=null) return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
                if(zip.getEntry("xl/workbook.xml")!=null) return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            }
        } catch(Exception e) { return "unsupported"; }
        finally { if(temp!=null) try {Files.deleteIfExists(temp);}catch(Exception ignored){} }
        return "unsupported";
    }
    private boolean starts(byte[] bytes,int... signature) {
        if(bytes.length<signature.length)return false;
        for(int i=0;i<signature.length;i++)if((bytes[i]&255)!=signature[i])return false;
        return true;
    }
}
