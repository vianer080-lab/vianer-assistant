import { useCallback, useEffect, useState } from 'react';
import { Alert, AppState, Linking, ScrollView, StatusBar, StyleSheet, Text, TouchableOpacity, View } from 'react-native';

const API_URL = 'https://vianer-assistant.expo.app';

async function openConnection(url, label) {
  try {
    await Linking.openURL(url);
  } catch {
    Alert.alert(label, 'Не удалось открыть подключение. Закройте окно и повторите.');
  }
}

export default function Socials() {
  const [youtube, setYoutube] = useState({ loading: true, configured: false, connected: false, channel: null });
  const [instagram, setInstagram] = useState({ loading: true, configured: false, connected: false, account: null });

  const refreshYoutube = useCallback(async () => {
    try {
      const response = await fetch(`${API_URL}/api/youtube/health`, { cache: 'no-store' });
      const data = await response.json();
      setYoutube({ loading: false, ...data });
    } catch {
      setYoutube((current) => ({ ...current, loading: false }));
    }
  }, []);

  const refreshInstagram = useCallback(async () => {
    try {
      const response = await fetch(`${API_URL}/api/instagram/health`, { cache: 'no-store' });
      const data = await response.json();
      setInstagram({ loading: false, ...data });
    } catch {
      setInstagram((current) => ({ ...current, loading: false }));
    }
  }, []);

  useEffect(() => {
    refreshYoutube();
    refreshInstagram();
    const subscription = AppState.addEventListener('change', (state) => {
      if (state === 'active') {
        refreshYoutube();
        refreshInstagram();
      }
    });
    return () => subscription.remove();
  }, [refreshYoutube, refreshInstagram]);

  const networks = [
    {
      name: 'Telegram',
      status: 'Работает',
      detail: 'MasterPick Georgia · канал и бот',
      active: true,
      actionLabel: 'Открыть Telegram →',
      action: () => openConnection('https://t.me/masterpick_georgia', 'Telegram'),
    },
    {
      name: 'YouTube',
      status: youtube.loading ? 'Проверка…' : youtube.connected ? 'Подключён' : 'Готов к подключению',
      detail: youtube.connected ? `Канал: ${youtube.channel?.title || 'YouTube'}` : 'Нажмите и подтвердите доступ к нужному каналу Google',
      active: youtube.connected,
      actionLabel: youtube.connected ? 'Обновить статус →' : 'Подключить YouTube →',
      action: youtube.connected ? refreshYoutube : () => openConnection(`${API_URL}/api/youtube/connect`, 'YouTube'),
    },
    {
      name: 'Instagram',
      status: instagram.loading ? 'Проверка…' : instagram.connected ? 'Подключён' : instagram.configured ? 'Готов к подключению' : 'Нужна настройка Meta',
      detail: instagram.connected
        ? `Аккаунт: @${instagram.account?.username || 'Instagram'}`
        : 'Подключение профессионального аккаунта MasterPick Global через Meta OAuth',
      active: instagram.connected,
      actionLabel: instagram.connected ? 'Обновить статус →' : 'Подключить Instagram →',
      action: instagram.connected ? refreshInstagram : () => openConnection(`${API_URL}/api/instagram/connect`, 'Instagram'),
    },
    {
      name: 'Pinterest',
      status: 'Готов к подключению',
      detail: 'Откроется существующий аккаунт Pinterest для подтверждения',
      actionLabel: 'Подключить Pinterest →',
      action: () => openConnection('https://www.pinterest.com/login/', 'Pinterest'),
    },
    {
      name: 'WhatsApp Business',
      status: 'Готов к подключению',
      detail: 'Откроется WhatsApp Business на этом телефоне',
      actionLabel: 'Подключить WhatsApp Business →',
      action: () => openConnection('whatsapp://send', 'WhatsApp Business'),
    },
  ];

  return <View style={s.screen}><StatusBar barStyle="light-content" backgroundColor="#0b1220"/><ScrollView contentContainerStyle={s.content}>
    <Text style={s.kicker}>СОЦСЕТИ</Text>
    <Text style={s.title}>Каналы</Text>
    <Text style={s.subtitle}>Нажмите нужную площадку, войдите в аккаунт и подтвердите разрешения.</Text>
    {networks.map((n) => <TouchableOpacity key={n.name} style={s.card} activeOpacity={0.7} onPress={n.action}>
      <View style={s.row}><Text style={s.name}>{n.name}</Text><View style={[s.badge,n.active?s.good:s.ready]}><Text style={s.badgeText}>{n.status}</Text></View></View>
      <Text style={s.detail}>{n.detail}</Text>
      <Text style={s.open}>{n.actionLabel}</Text>
    </TouchableOpacity>)}
    <View style={s.plan}><Text style={s.planTitle}>Обновление подключения</Text><Text style={s.planText}>Все карточки теперь работают как кнопки. Сначала подключаем YouTube, затем Instagram, Pinterest и WhatsApp Business.</Text></View>
  </ScrollView></View>;
}

const s=StyleSheet.create({
  screen:{flex:1,backgroundColor:'#0b1220'},
  content:{padding:20,paddingTop:34,paddingBottom:40},
  kicker:{color:'#38bdf8',fontWeight:'800',letterSpacing:2,fontSize:13},
  title:{color:'#fff',fontSize:30,fontWeight:'800',marginTop:7},
  subtitle:{color:'#94a3b8',fontSize:16,lineHeight:23,marginTop:8,marginBottom:24},
  card:{backgroundColor:'#111c2e',borderRadius:18,padding:18,marginBottom:13,borderWidth:1,borderColor:'#22304a'},
  row:{flexDirection:'row',alignItems:'center',justifyContent:'space-between',gap:10},
  name:{color:'#fff',fontSize:18,fontWeight:'800',flex:1},
  badge:{borderRadius:20,paddingHorizontal:10,paddingVertical:6,maxWidth:'48%'},
  good:{backgroundColor:'#14532d'},
  ready:{backgroundColor:'#1e3a8a'},
  badgeText:{color:'#fff',fontSize:11,fontWeight:'800',textAlign:'center'},
  detail:{color:'#94a3b8',fontSize:14,lineHeight:20,marginTop:9},
  open:{color:'#7dd3fc',fontWeight:'800',marginTop:12},
  plan:{backgroundColor:'#172554',borderRadius:18,padding:18,marginTop:8},
  planTitle:{color:'#bfdbfe',fontSize:15,fontWeight:'800'},
  planText:{color:'#dbeafe',fontSize:14,lineHeight:22,marginTop:7},
});
