import { ScrollView, StatusBar, StyleSheet, Text, TouchableOpacity, View } from 'react-native';

const queue = [
  { source: 'Amazon', day: 'Пн · Вт · Чт · Пт · Вс', state: 'Активно' },
  { source: 'AliExpress RU&CIS', day: 'Ср · Сб', state: 'Активно' },
];

export default function AutopostScreen() {
  return (
    <View style={styles.screen}>
      <StatusBar barStyle="light-content" backgroundColor="#0b1220" />
      <ScrollView contentContainerStyle={styles.content}>
        <Text style={styles.kicker}>АВТОПОСТИНГ</Text>
        <Text style={styles.title}>Публикации</Text>
        <Text style={styles.subtitle}>Расписание, источники и очередь автоматических постов.</Text>

        <View style={styles.summary}>
          <View><Text style={styles.small}>Режим</Text><Text style={styles.big}>Автоматический</Text></View>
          <View style={styles.dot} />
        </View>

        <Text style={styles.section}>Источники</Text>
        {queue.map((item) => (
          <View key={item.source} style={styles.card}>
            <View style={styles.row}><Text style={styles.source}>{item.source}</Text><Text style={styles.active}>{item.state}</Text></View>
            <Text style={styles.day}>{item.day}</Text>
          </View>
        ))}

        <View style={styles.card}>
          <Text style={styles.small}>Время публикации</Text>
          <Text style={styles.big}>11:17 · Тбилиси</Text>
          <Text style={styles.note}>Один автоматический партнёрский пост в день. Повторная публикация в тот же день блокируется.</Text>
        </View>

        <TouchableOpacity style={styles.button} activeOpacity={0.75}>
          <Text style={styles.buttonText}>Ручной запуск — следующий этап</Text>
        </TouchableOpacity>
        <Text style={styles.warning}>Кнопка пока информационная. Прямой безопасный запуск GitHub Action из приложения подключим через отдельный backend, без хранения секретов в APK.</Text>
      </ScrollView>
    </View>
  );
}

const styles = StyleSheet.create({
  screen:{flex:1,backgroundColor:'#0b1220'},content:{padding:20,paddingTop:34,paddingBottom:40},kicker:{color:'#38bdf8',fontWeight:'800',letterSpacing:2,fontSize:13},title:{color:'#fff',fontSize:30,fontWeight:'800',marginTop:7},subtitle:{color:'#94a3b8',fontSize:16,lineHeight:23,marginTop:8,marginBottom:24},summary:{backgroundColor:'#111c2e',borderRadius:18,padding:18,flexDirection:'row',alignItems:'center',justifyContent:'space-between',borderWidth:1,borderColor:'#22304a',marginBottom:24},small:{color:'#8fa3bf',fontSize:13},big:{color:'#fff',fontSize:18,fontWeight:'800',marginTop:5},dot:{width:12,height:12,borderRadius:6,backgroundColor:'#22c55e'},section:{color:'#fff',fontSize:20,fontWeight:'800',marginBottom:12},card:{backgroundColor:'#111c2e',borderRadius:18,padding:18,marginBottom:13,borderWidth:1,borderColor:'#22304a'},row:{flexDirection:'row',justifyContent:'space-between',alignItems:'center'},source:{color:'#fff',fontSize:17,fontWeight:'800',flex:1},active:{color:'#86efac',fontSize:13,fontWeight:'700'},day:{color:'#94a3b8',fontSize:14,marginTop:8},note:{color:'#94a3b8',fontSize:14,lineHeight:20,marginTop:9},button:{backgroundColor:'#1d4ed8',borderRadius:16,padding:17,alignItems:'center',marginTop:4},buttonText:{color:'#fff',fontWeight:'800',fontSize:15},warning:{color:'#718096',fontSize:12,lineHeight:18,marginTop:10,textAlign:'center'}
});
