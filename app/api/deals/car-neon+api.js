const PRODUCT_URL = 'https://www.temu.com/gr-en/car-led-neon-light-usb-powered-racing-neon-sign-with-switch--levels-of-brightness-adjustable-suitable-for-beautiful-neon-decoration-in-bedrooms-auto-repair-shops-bars-parties-and-bedroom-wall-decor-g-606054914064703.html?top_gallery_url=https%3A%2F%2Fimg.kwcdn.com%2Fproduct%2Ffancy%2F91e8875b-5e4b-47d7-a8c3-7b1c841042c9.jpg&spec_gallery_id=43631252318&share_token=KmKbcaIdGMUby62-lOO30j9k1nVTLcaP9JVcDZszw0epb_Q9QMnPN5IzDIx5plGSsof1m-iiIutfH6P6B857b2tzp_cu1_eMnkx9Lpr-owqHIJv-WbB7lbssquqyzFsoncBqVZTksufHrrsh7VzBJkVts0iMeM2eCjNjF2gks_X&refer_page_el_sn=209279&_x_vst_scene=adg&_x_ads_channel=kol_affiliate&_x_campaign=affiliate&_x_cid=4080930786kol_affiliate&_x_ads_csite=search&_x_ns_adg_stid=96881396e30e45c0_nli326p&refer_page_name=kuiper&refer_page_id=14021_1787806876538_qrnajimr55&refer_page_sn=14021&_x_sessn_id=aa7ul3jtyc';

export function GET() {
  return Response.redirect(PRODUCT_URL, 302);
}

export function HEAD() {
  return new Response(null, { status: 302, headers: { Location: PRODUCT_URL } });
}
