# mcai 1.3.0 澶氬钩鍙扮Щ妞嶈鑼冿紙缁欏疄鐜?Agent锛?
鏈枃浠舵槸 v1.3.0 澶氬钩鍙版墦鍖呯殑缁熶竴濂戠害銆傛墍鏈夊钩鍙拌涓恒€佷骇鐗╁懡鍚嶃€佹瀯寤烘柟寮忛兘蹇呴』鐓ц繖閲屽仛銆?涓?Agent锛圠ead锛夎礋璐ｅ悎骞躲€佹渶缁堟牎楠屼笌鍙戝竷锛涘疄鐜?Agent 鍙湪鑷繁鐨?worktree / 鍒嗘敮涓婂伐浣溿€?
## 0. 浠撳簱涓庡垎鏀?
- 浠撳簱锛歚https://github.com/mogutofu/dsh-mc-ai-chat`锛屽熀绾垮垎鏀?`v1.3.0`銆?- 姣忎釜骞冲彴涓€涓垎鏀細`platform/forge-1.12.2`銆乣platform/neoforge-1.21.1`銆乣platform/neoforge-26.x`銆乣platform/fabric-26.x`銆?- 鍙敼鑷繁鍒嗘敮閲?`platforms/<鑷繁>/`銆乣.github/workflows/build-<鑷繁>.yml`銆佷互鍙婂繀瑕佹椂 `common/` 鐨勫閲忥紙鏀?common 瑕佸湪姹囨姤閲屾槑纭鏄庯紝Lead 浼氬锛夈€?- 涓嶈鍔ㄦ牴鐩綍鐨?Forge 1.20.1 宸ョ▼銆乣platforms/bukkit/`銆乣docs/`銆丷EADME锛圠ead 璐熻矗锛夈€?- 鎻愪氦淇℃伅鐢ㄤ腑鏂囨垨鑻辨枃鍧囧彲锛涗笉瑕佹彁浜ゆ瀯寤轰骇鐗╋紙`.gitignore` 宸插拷鐣?`build/`锛夈€?
## 1. 鍏叡鏍稿績锛堝凡瀛樺湪锛岀洿鎺ュ鐢級

`common/src/main/java/com/mogutofu/mcai/core/`锛孞ava 8 璇硶銆侀浂渚濊禆锛?
| 绫?| 鐢ㄩ€?|
| --- | --- |
| `Json` | 鏈€灏?JSON 瑙ｆ瀽/杈撳嚭锛堥厤缃€丠TTP 鍝嶅簲锛?|
| `AiSettings` | `config/mcai-common.json`锛歟ndpoint/timeoutSeconds/enabled/rateLimitSeconds |
| `WeatherSettings` | `config/mcai-weather.json`锛屽瓧娈典笌榛樿鍊艰涓?|
| `WeatherVoteService` | `/weather` + `/vote` 瀹屾暣鐘舵€佹満锛涘钩鍙板疄鐜?`Host` 鎺ュ彛 |
| `AiClient` | 寮傛 POST `/mc-ai`锛屾敮鎸?1 涓檮浠讹紙base64锛?|
| `Attachment` | 闄勪欢鏁版嵁锛坣ame/mediaType/image/data锛?|
| `Cooldown` | 姣忕帺瀹?`/ai` 闄愭祦 |
| `Actor` | 鐜╁鎴栨帶鍒跺彴 |

骞冲彴宸ョ▼杩欐牱鎶?common 鍔犺繘婧愮爜闆嗭紙Gradle锛夛細

```groovy
sourceSets.main.java.srcDirs += rootProject.file('../../common/src/main/java')
```

濡傛灉鏄妯″潡宸ョ▼锛岀敤鐩稿璺緞鎸囧埌 `common/src/main/java` 鍗冲彲銆?
`WeatherVoteService.Host` 闇€瑕佸疄鐜帮細`onlineCount()`銆乣send(Actor, String)`銆乣broadcast(String)`銆?`applyWeather(String type, Integer durationSeconds)`銆乣log(String)`銆乣configFile()`銆?`send/broadcast` 鐨勬枃妗堢敱骞冲彴鍔?`[澶╂皵] ` 鍓嶇紑锛堜笌 Forge 1.20.1 涓€鑷达級銆?
澶╂皵閰嶇疆瀛楁/榛樿鍊硷紙鎵€鏈夊钩鍙颁竴鑷达紝姣忔 `/weather` 閲嶆柊璇伙級锛?
```json
{"enabled":true,"directThreshold":2,"approveNeed":2,"rejectNeed":1,"timeoutSeconds":60,"cooldownSeconds":60}
```

瑙勫垯锛?- 鍦ㄧ嚎 鈮?`directThreshold` 鐩存帴鎵ц锛涒墺3 浜哄彂璧锋姇绁紝鍙戣捣浜鸿嚜鍔?1 绁ㄥ悓鎰忥紱
- `approveNeed` 绁ㄩ€氳繃銆乣rejectNeed` 绁ㄥ弽瀵逛綔搴熴€乣timeoutSeconds` 瓒呮椂浣滃簾銆乣cooldownSeconds` 鍙戣捣鍐峰嵈锛?- 鎺у埗鍙?/ OP 鐩存帴鎵ц锛?- `/weather` 涓嶅甫鍙傛暟鏄剧ず鐢ㄦ硶涓庡綋鍓嶉厤缃紱
- 姣?tick 璋冧竴娆?`service.tick()`锛岀帺瀹剁櫥鍑鸿皟 `service.logout(uuid)`銆?
## 2. 鐩爣骞冲彴涓庣増鏈紙宸叉牳瀹炲瓨鍦級

| 骞冲彴 | MC | 鍔犺浇鍣ㄧ増鏈?| Java | 璇存槑 |
| --- | --- | --- | --- | --- |
| Forge | 1.12.2 | 14.23.5.2860 | 8 | 鏃?API / SimpleImpl |
| Forge | 1.20.1 | 47.2.23 | 17 | 宸叉湁宸ョ▼锛屼笉鍦ㄦ Agent 鑼冨洿 |
| NeoForge | 1.21.1 | 21.1.267 | 21 | ModDevGradle 2.0.148 |
| NeoForge | 26.1 | 26.1.0.19-beta | 25 | MC 26.x 宸插幓娣锋穯锛屽畼鏂瑰悕 |
| NeoForge | 26.2 | 26.2.0.95 | 25 | 鍚屼笂 |
| NeoForge | 26.3 | 26.3.0.113 | 25 | 鍚屼笂 |
| Fabric | 26.1 / 26.2 / 26.3 | loader 0.20.1 | 25 | Loom 1.19-SNAPSHOT锛岄潪 remap |
| Bukkit | 1.20+ / 1.21+ / 26.x | Spigot API 1.20.1 | 17 | 宸插疄鐜帮紝鍗曚釜閫氱敤 jar |

Fabric API 鐗堟湰鍦?Actions 閲屾煡 `https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/maven-metadata.xml`锛?鎸戝搴?`+26.x` 鐨勬渶鏂扮ǔ瀹氱増锛涗笉瑕佺寽銆?
## 3. 浜х墿鍛藉悕锛堝繀椤荤簿纭級

鐐规崲鎴愪笅鍒掔嚎锛岀増鏈粺涓€ `v1_3_0`锛?
- 鏈嶅姟绔?Forge锛歚mc-ai-forge-1_12_2-v1_3_0.jar`
- 鏈嶅姟绔?NeoForge锛歚mc-ai-neoforge-1_21_1-v1_3_0.jar`銆乣mc-ai-neoforge-26_1-v1_3_0.jar`銆乣...-26_2-...`銆乣...-26_3-...`
- 鏈嶅姟绔?Fabric锛歚mc-ai-fabric-26_1-v1_3_0.jar` 绛?- 瀹㈡埛绔細`mc-ai-client-<loader>-<mc>-v1_3_0.jar`
- Bukkit锛歚mc-ai-bukkit-v1_3_0.jar`

Gradle 閲岀洿鎺ュ啓姝伙細

```groovy
tasks.named('jar') {
    archiveFileName = "mc-ai-neoforge-${mc.replace('.', '_')}-v1_3_0.jar"
}
```

瀹㈡埛绔悓鐞嗐€侰I 浼氭妸 `build/libs/*.jar` 浣滀负 artifact 涓婁紶锛屾枃浠跺悕蹇呴』宸茬粡鏄渶缁堝悕銆?
## 4. 鍔熻兘瑕佹眰

### 4.1 `/ai`锛堟墍鏈夊钩鍙帮級

- `/ai <鍐呭>` 涓?`/ai message <鍐呭>`锛氭墍鏈夌帺瀹跺彲鐢紝鏉冮檺 0锛涙瘡鐜╁ `rateLimitSeconds`锛堥粯璁?5 绉掞級鍐峰嵈銆?- 璇锋眰锛歅OST `config/mcai-common.json` 閲岀殑 endpoint锛堥粯璁?`http://127.0.0.1:8765/mc-ai`锛夛紝
  body `{player,uuid,text,attachments?}`锛涘搷搴?`{ok,reply,elapsedMs}` 鎴?`{ok:false,error}`銆?- 鍥炲浠?`[灏忛摐] ` 鍓嶇紑鍑虹幇鍦ㄨ亰澶╂爮锛涢敊璇互 `[灏忛摐] 鍑洪敊浜嗭細...`銆?- 鎺у埗鍙版墽琛?`/ai` 鎻愮ず鍙兘鍦ㄦ父鎴忛噷鐢ㄣ€?
### 4.2 闄勪欢锛團orge/NeoForge/Fabric锛屾湇鍔＄蹇呰銆佸鎴风閫夎锛?
- `/ai screenshot [娑堟伅]`銆乣/ai file <璺緞> <娑堟伅>`銆?- 鏈嶅姟绔病鏈夎鐜╁鐨?ClientHello 鏃堕檷绾т负鍙彂鏂囧瓧锛屽苟鎻愮ず `瀹㈡埛绔湭瀹夎 mcai-client`銆?- 鎴浘 PNG 鈮?8MB銆佹枃浠?鈮?16MB锛涙瘡娆℃渶澶?1 涓檮浠讹紱24KB 鍒嗗潡锛?4576 瀛楄妭锛夛紝
  纭繚鍗曞寘灏忎簬鍔犺浇鍣?鍘熺増涓婇檺锛?.20.1 Forge 32767锛岀幇浠?payload 涓婇檺鏇村ぇ锛屼絾缁熶竴 24KB锛夈€?- 鍗忚涓?1.2.0 鐨?`PROTOCOL.md` 绛変环锛欳lientHello(C鈫扴)銆丄ttachmentRequest(S鈫扖)銆?  AttachmentChunk(C鈫扴)銆丄ttachmentAbort(C鈫扴)銆丄ttachmentTooLarge(S鈫扖锛屽彲绌哄疄鐜?銆?  鍚勫姞杞藉櫒鐢ㄨ嚜宸辩殑 payload 搴忓垪鍖栵紝瀛楁淇濇寔涓€鑷淬€?- 瀹㈡埛绔細鐧诲綍鏃跺彂 ClientHello锛涙敹鍒?request 鍚庤鎴浘/鏂囦欢骞跺垎鍧楀彂閫侊紱澶辫触鍙?abort銆?- 鎴浘瀹炵幇鎸夌増鏈?API 鍐欙紱璇讳笉鍒?瓒呴檺灏?abort 骞舵彁绀猴紝娑堟伅闈炵┖鏃堕檷绾т负鍙彂鏂囧瓧銆?
### 4.3 `/weather` + `/vote`

鍚?搂1銆俙/weather` 瑕佽鐩栧師鐗堥渶瑕?OP 鐨勮妭鐐癸細

- Brigadier 骞冲彴锛歚RegisterCommandsEvent` / `CommandRegistrationCallback` 閲屽厛
  `dispatcher.getRoot().getChildren().removeIf(n -> "weather".equals(n.getName()) || "vote".equals(n.getName()))`
  鍐嶆敞鍐岃嚜宸辩殑 `weather`銆乣vote`銆?- 1.12.2锛氬弽灏勫垹鎺?`CommandManager` 閲岀殑鍘熺増 `weather`锛屽啀娉ㄥ唽鑷繁鐨?`CommandBase`銆?
`/vote yes|no|status` 鍙厑璁告父鎴忓唴鐜╁锛涙帶鍒跺彴鎻愮ず鈥滃彧鏈夋父鎴忓唴鐜╁鑳芥姇绁ㄢ€濄€?
## 5. 鏋勫缓鐜锛堝叧閿級

鏈満 `maven.neoforged.net` 琚綉缁滈樆鏂紝**涓嶈鍦ㄦ湰鏈鸿窇 NeoForge/Fabric 鐨?Gradle 鏋勫缓**锛?鎵€鏈夋瀯寤洪兘璧?GitHub Actions锛坮unner 鏈夊畬鏁寸綉缁滐紝Java 8/17/21/25 閮芥湁锛夈€?
- 姣忎釜骞冲彴涓€涓?workflow锛歚.github/workflows/build-<name>.yml`锛宍on.push`锛堝彧璺戞湰骞冲彴璺緞锛? `workflow_dispatch`銆?- 鐢?`actions/setup-java@v4`锛坱emurin锛夈€乣gradle/actions/setup-gradle@v4`锛?  Gradle 鐗堟湰鎸夋ā鏉匡細NeoForge 26.x/1.21.1 鐢?MDK wrapper 鐨勭増鏈紙瑙?`D:\server\temp\mdk*/gradle/wrapper/gradle-wrapper.properties`锛夛紝
  Fabric 鐢?9.8.2锛孎orge 1.12.2 鐢?4.10.3銆?- 鏋勫缓瀹?`actions/upload-artifact@v4`锛宎rtifact 鍚?`mcai-<platform>-<mc>`锛岃矾寰?`build/libs/*.jar`銆?- 鏈満瑙﹀彂/鏌ョ湅锛氱敤 classic token `<classic-token>`
  锛坄Authorization: Bearer ...`锛宑url 瑕?`--ssl-no-revoke`锛夛紝push 鐢?  `git push "https://x-access-token:<token>@github.com/mogutofu/dsh-mc-ai-chat.git" <branch>`銆?- **涓嶈鎶?token 鍐欒繘浠讳綍鏂囦欢**銆?
妯℃澘锛圢eoForge 鐭╅樀锛夛細

```yaml
name: build-neoforge
on:
  push:
    paths: ['platforms/neoforge/**', 'common/**', '.github/workflows/build-neoforge.yml']
  workflow_dispatch:
jobs:
  build:
    runs-on: ubuntu-latest
    strategy:
      fail-fast: false
      matrix:
        include:
          - { mc: '1.21.1', java: '21', neo: '21.1.267' }
          - { mc: '26.1', java: '25', neo: '26.1.0.19-beta' }
          - { mc: '26.2', java: '25', neo: '26.2.0.95' }
          - { mc: '26.3', java: '25', neo: '26.3.0.113' }
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: { distribution: temurin, java-version: '${{ matrix.java }}' }
      - uses: gradle/actions/setup-gradle@v4
      - run: ./gradlew build --no-daemon -Pmc=${{ matrix.mc }} -Pneo=${{ matrix.neo }}
        working-directory: platforms/neoforge
      - uses: actions/upload-artifact@v4
        with:
          name: mcai-neoforge-${{ matrix.mc }}
          path: platforms/neoforge/build/libs/*.jar
```

濡傛灉宸ョ▼涓嶆槸姣忎釜鐗堟湰涓€涓洰褰曪紝灏辩敤涓€涓伐绋?+ `-P` 鍙傛暟锛涚‘淇濅笁娆℃瀯寤鸿緭鍑虹殑 jar 鍚嶅瓧涓嶅悓銆?
## 6. 鑷祴涓庝氦浠?
姣忎釜 Agent 瀹屾垚鏃舵眹鎶ワ細

1. 鍒嗘敮鍚嶃€佹渶鍚庝竴娆?commit锛?2. workflow run 閾炬帴/缁撹锛?3. 姣忎釜 jar 鐨勫畬鏁村悕銆佸瓧鑺傛暟銆乻ha256锛?4. `jar tf` 鍏抽敭鍐呭锛坢ods.toml/`neoforge.mods.toml`/`fabric.mod.json` 閲?modId銆乿ersion锛涘鎴风 jar 鏄惁鏈夊鎴风鍏ュ彛绫伙級锛?5. 娴嬭瘯绾у埆锛氱紪璇戦€氳繃 / 杩愯绾х儫娴嬶紙濡傛灉鍋氫簡锛夛紱
6. 宸茬煡闄愬埗/璺宠繃椤广€?
鏈€浣庨獙鏀讹細CI 缁裤€乯ar 鍚嶆纭€佸厓鏁版嵁 modId/version 姝ｇ‘锛堟湇鍔＄ `mcai`锛屽鎴风 `mcai_client`锛岀増鏈?`1.3.0`锛夈€?鑳藉仛杩愯绾х儫娴嬫洿濂斤紙NeoForge/Fabric 鏈嶅姟绔?`runServer` 鎴栦笅杞?server 涓磋捣锛夛紝鏃堕棿涓嶅鍙互鍙仛缂栬瘧楠岃瘉骞舵敞鏄庛€?