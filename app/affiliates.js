import { ScrollView, StatusBar, StyleSheet, Text, View } from 'react-native';

const programs = [
  { name: 'Amazon US', status: 'Активна', detail: 'Основной источник · 5 дней в неделю', ok: true },
  { name: 'AliExpress RU&CIS', status: 'Ссылка сломана', detail: 'Одобрена, но сохранённая ссылка зацикливается · публикации отключены', ok: false, pending: true },
  { name: 'Joom', status: 'Отклонена', detail: 'Ссылки не публикуются', ok: false },
  { name: 'Impact', status: 'Marketplace отклонён', detail: 'Аккаунт 7643000 · только прямые приглашения брендов', ok: false, pending: true },
  { name: 'Temu', status: 'Активна', detail: 'Партнёрская ссылка проверена 02.09.2026', ok: true },
];

export default function AffiliatesScreen() {
  return <View style={styles.screen}><StatusBar barStyle="light-content" backgroundColor="#0b1220"/><ScrollView contentContainerStyle={styles.content}><Text style={styles.kicker}>ПАРТНЁРКИ</Text><Text style={styles.title}>Программы</Text><Text style={styles.subtitle}>Статусы источников, которые используются MasterPick.</Text>{programs.map((p)=><View key={p.name} style={styles.card}><View style={styles.row}><Text style={styles.name}>{p.name}</Text><View style={[styles.badge,p.ok?styles.good:p.pending?styles.pending:styles.bad]}><Text style={styles.badgeText}>{p.status}</Text></View></View><Text style={styles.detail}>{p.detail}</Text></View>)}<View style={styles.info}><Text style={styles.infoTitle}>Безопасная схема</Text><Text style={styles.infoText}>Партнёрские ключи и секреты не сохраняются внутри APK. Master Hub будет получать только необходимые статусы и команды через защищённый сервер.</Text></View></ScrollView></View>;
}

const styles=StyleSheet.create({screen:{flex:1,backgroundColor:'#0b1220'},content:{padding:20,paddingTop:34,paddingBottom:40},kicker:{color:'#38bdf8',fontWeight:'800',letterSpacing:2,fontSize:13},title:{color:'#fff',fontSize:30,fontWeight:'800',marginTop:7},subtitle:{color:'#94a3b8',fontSize:16,lineHeight:23,marginTop:8,marginBottom:24},card:{backgroundColor:'#111c2e',borderRadius:18,padding:18,marginBottom:13,borderWidth:1,borderColor:'#22304a'},row:{flexDirection:'row',alignItems:'center',justifyContent:'space-between',gap:10},name:{color:'#fff',fontSize:18,fontWeight:'800',flex:1},detail:{color:'#94a3b8',fontSize:14,lineHeight:20,marginTop:9},badge:{borderRadius:20,paddingHorizontal:10,paddingVertical:6},good:{backgroundColor:'#14532d'},bad:{backgroundColor:'#7f1d1d'},pending:{backgroundColor:'#713f12'},badgeText:{color:'#fff',fontSize:11,fontWeight:'800'},info:{backgroundColor:'#172554',borderRadius:18,padding:18,marginTop:8},infoTitle:{color:'#bfdbfe',fontSize:15,fontWeight:'800'},infoText:{color:'#dbeafe',fontSize:14,lineHeight:21,marginTop:7}});
