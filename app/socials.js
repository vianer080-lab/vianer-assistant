import { useCallback, useEffect, useState } from 'react';
import { AppState, Linking, ScrollView, StatusBar, StyleSheet, Text, TouchableOpacity, View } from 'react-native';

const API_URL = 'https://vianer-assistant.expo.app';
const baseNetworks = [
  { name: 'Telegram', status: 'Работает', detail: 'MasterPick Georgia · канал и бот', url: 'https://t.me/masterpick_georgia', active: true },
  { name: 'Instagram', status: 'Не подключён', detail: 'Нужно создать/подключить рабочий аккаунт' },
  { name: 'Pinterest', status: 'Аккаунт есть', detail: 'Подключение к Master Hub ещё не настроено' },
  { name: 'WhatsApp Business', status: 'Не подключён', detail: 'Подготовим после основных соцсетей' },
];

export default function Socials() {
  const [youtube, setYoutube] = useState({ loading: true, configured: false, connected: false, channel: null });
  const refreshYoutube = useCallback(async () => {
    try {
      const response = await fetch(`${API_URL}/api/youtube/health`, { cache: 'no-store' });
      const data = await response.json();
      setYoutube({ loading: false, ...data });
    } catch {
      setYoutube((current) => ({ ...current, loading: false }));
    }
  }, []);

  useEffect(() => {
    refreshYoutube();
    const subscription = AppState.addEventListener('change', (state) => state === 'active' && refreshYoutube());
    return () => subscription.remove();
  }, [refreshYoutube]);

  const youtubeNetwork = {
    name: 'YouTube',
    status: youtube.loading ? 'Проверка…' : youtube.connected ? 'Подключён' : 'Требует входа',
    detail: youtube.connected ? `Канал: ${youtube.channel?.title || 'YouTube'}` : 'Нажмите один раз и подтвердите доступ Google',
    active: youtube.connected,
    action: youtube.connected ? refreshYoutube : () => Linking.openURL(`${API_URL}/api/youtube/connect`),
    actionLabel: youtube.connected ? 'Обновить статус' : 'Подключить YouTube →',
  };
  const networks = [baseNetworks[0], youtubeNetwork, ...baseNetworks.slice(1)];

  return <View style={s.screen}><StatusBar barStyle="light-content" backgroundColor="#0b1220"/><ScrollView contentContainerStyle={s.content}>
    <Text style={s.kicker}>СОЦСЕТИ</Text><Text style={s.title}>Каналы</Text><Text style={s.subtitle}>Единая панель публикаций и настоящих статусов социальных площадок.</Text>
    {networks.map((n) => <TouchableOpacity key={n.name} style={s.card} activeOpacity={(n.url || n.action) ? 0.7 : 1} onPress={() => n.action ? n.action() : n.url && Linking.openURL(n.url)}>
      <View style={s.row}><Text style={s.name}>{n.name}</Text><View style={[s.badge,n.active?s.good:s.wait]}><Text style={s.badgeText}>{n.status}</Text></View></View>
      <Text style={s.detail}>{n.detail}</Text>{(n.url || n.action) && <Text style={s.open}>{n.actionLabel || 'Открыть →'}</Text>}
    </TouchableOpacity>)}
    <View style={s.plan}><Text style={s.planTitle}>Порядок подключения</Text><Text style={s.planText}>1. Telegram — работает.  2. YouTube — текущий этап.  3. Instagram.  4. Pinterest. После этого объединим публикацию одного материала сразу на несколько площадок.</Text></View>
  </ScrollView></View>;
}

const s=StyleSheet.create({screen:{flex:1,backgroundColor:'#0b1220'},content:{padding:20,paddingTop:34,paddingBottom:40},kicker:{color:'#38bdf8',fontWeight:'800',letterSpacing:2,fontSize:13},title:{color:'#fff',fontSize:30,fontWeight:'800',marginTop:7},subtitle:{color:'#94a3b8',fontSize:16,lineHeight:23,marginTop:8,marginBottom:24},card:{backgroundColor:'#111c2e',borderRadius:18,padding:18,marginBottom:13,borderWidth:1,borderColor:'#22304a'},row:{flexDirection:'row',alignItems:'center',justifyContent:'space-between',gap:10},name:{color:'#fff',fontSize:18,fontWeight:'800',flex:1},badge:{borderRadius:20,paddingHorizontal:10,paddingVertical:6},good:{backgroundColor:'#14532d'},wait:{backgroundColor:'#713f12'},badgeText:{color:'#fff',fontSize:11,fontWeight:'800'},detail:{color:'#94a3b8',fontSize:14,lineHeight:20,marginTop:9},open:{color:'#7dd3fc',fontWeight:'700',marginTop:10},plan:{backgroundColor:'#172554',borderRadius:18,padding:18,marginTop:8},planTitle:{color:'#bfdbfe',fontSize:15,fontWeight:'800'},planText:{color:'#dbeafe',fontSize:14,lineHeight:22,marginTop:7}});
