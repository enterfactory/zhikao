package com.zhikao.modules.crawler.image;
import com.zhikao.common.BusinessException;
import com.zhikao.config.CrawlerProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.net.*;
import java.util.*;
@Component @RequiredArgsConstructor
public class ImageTargetPolicy {
 private final CrawlerProperties properties;
 public void requireAllowed(URI uri)throws UnknownHostException {
  String host=uri.getHost();
  if(host==null || uri.getRawUserInfo()!=null || !Set.of("http","https").contains(uri.getScheme()) || (uri.getPort()!=-1 && uri.getPort()!=80 && uri.getPort()!=443))throw new BusinessException("图片地址不受支持");
  if(properties.getImageAllowedHosts().stream().noneMatch(h->h.equalsIgnoreCase(host)))throw new BusinessException("外链图片域名未列入受控白名单，请使用本地配图");
  for(InetAddress address:InetAddress.getAllByName(host))if(!publicAddress(address))throw new BusinessException("图片地址解析到非公网目标");
 }
 public static boolean publicAddress(InetAddress address) {
  if(address.isAnyLocalAddress()||address.isLoopbackAddress()||address.isLinkLocalAddress()||address.isSiteLocalAddress()||address.isMulticastAddress())return false;
  byte[] b=address.getAddress();int first=b[0]&255,second=b[1]&255;
  if(b.length==4)return first!=0 && first!=10 && first!=127 && !(first==100 && second>=64 && second<=127)
   && !(first==169&&second==254) && !(first==172&&second>=16&&second<=31) && !(first==192&&second==168)
   && first<224 && !(first==198&&(second==18||second==19));
  return (first&0xfe)!=0xfc && !(first==0xfe && (second&0xc0)==0x80);
 }
}
