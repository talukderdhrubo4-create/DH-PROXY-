# FloatProxy — SOCKS5 diagnosis patch

এই patch-এ যা বদলানো হয়েছে:
- SOCKS5 greeting ও username/password authentication response যাচাই করা হয়।
- Proxy শুধু TCP socket accept করে কিনা নয়, SOCKS5 CONNECT request দিয়ে remote TCP connection-ও যাচাই করা হয়।
- Username/password extras service intent-এ পাঠানো হয়, যাতে পরের tunnel-engine integration-এ ব্যবহার করা যায়।
- UI-তে নকল traffic byte counter বন্ধ করা হয়েছে।

## গুরুত্বপূর্ণ সীমাবদ্ধতা

এই patch একাই ফোনের internet traffic SOCKS5 দিয়ে চালু করবে না। বর্তমান `ProxyVpnService` শুধু Android `VpnService.Builder` দিয়ে TUN interface তৈরি করে; TUN থেকে আসা IP packet-গুলোকে SOCKS5 connection-এ রূপান্তর করার **tun2socks native engine** project-এ নেই। Engine ছাড়া VPN interface traffic ধরে ফেলতে পারে, কিন্তু সেটি proxy দিয়ে forward করতে পারে না।

সম্পূর্ণ সমাধানের জন্য Android-compatible tun2socks engine (যেমন HevSocks5Tunnel) এর Android AAR/native libraries যুক্ত করে service থেকে `TProxyStartService(config, vpnInterface.fd)` চালাতে হবে, এবং stop/disconnect-এ native engine বন্ধ করতে হবে। এই ZIP-এ native AAR যোগ করা হয়নি এবং Android build চালিয়ে যাচাই করা হয়নি; তাই এটিকে fully working internet-proxy build বলা যাবে না।
