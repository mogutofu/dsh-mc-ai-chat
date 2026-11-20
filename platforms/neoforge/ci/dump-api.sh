#!/usr/bin/env bash
# mcai NeoForge 26.x API 自查脚本（只在 CI 里跑）。
#
# 构建（不管成功失败）之后执行，把这次要核对的 NeoForge / Minecraft 类打到 Actions 日志里：
#   1) 打印 NFRT 的 artifact manifest，并按里面的路径收集 jar；
#   2) 解开所有 sources jar（NeoForge 有源码，MC 一般没有）到 /tmp/neo-src；
#   3) 用编译 classpath javap 一批 Minecraft 类，确认方法名（26.x 改动大）。
set -uo pipefail

echo "===== env ====="
java -version 2>&1 | head -3

echo
echo "===== NFRT manifest ====="
for m in build/tmp/createMinecraftArtifacts/nfrt_artifact_manifest.properties server/build/tmp/createMinecraftArtifacts/nfrt_artifact_manifest.properties; do
  if [ -f "$m" ]; then
    echo "--- $m"
    cat "$m"
  fi
done

echo
echo "===== jars under build/ ====="
find build -name '*.jar' -printf '%s\t%p\n' 2>/dev/null | sort -n | head -40 || echo '(none)'

# manifest 里记的 artifact 路径（形如 /home/runner/.gradle/.../xxx.jar）
ART_JARS=$(grep -ohE '/[^ =:]+\.jar' build/tmp/createMinecraftArtifacts/*.properties \
  server/build/tmp/createMinecraftArtifacts/*.properties 2>/dev/null | sort -u)
echo
echo "===== artifacts from manifest ====="
echo "${ART_JARS:-(none)}"

rm -rf /tmp/neo-src
mkdir -p /tmp/neo-src
SRC_JARS=$(printf '%s\n' "$ART_JARS" \
  $(find build "$HOME/.gradle/caches/modules-2/files-2.1" -name '*sources*.jar' 2>/dev/null) \
  | grep -E 'sources.*\.jar$' | sort -u | tr '\n' ' ')
echo
echo "===== sources jars ====="
echo "${SRC_JARS:-(none)}"
for j in $SRC_JARS; do
  echo "-- extracting $j"
  unzip -qo "$j" -d /tmp/neo-src 2>/dev/null
done
echo "extracted java files: $(find /tmp/neo-src -name '*.java' | wc -l)"

# 编译 classpath：MDG 生成的 MC 类在这里，javap 用
CP=$(./gradlew -q --no-daemon printCompileCp 2>/dev/null | sed -n 's/^CP_SERVER=//p;s/^CP_CLIENT=//p' | tr '\n' ':')
echo
echo "===== compile classpath entries: $(echo "$CP" | tr ':' '\n' | grep -c .) ====="

jpk() {
  echo
  echo "########## $1 | $2"
  javap -classpath "$CP" "$1" 2>/dev/null | grep -E "$2" | head -30 || echo '  (no match / class missing)'
}

jpk net.minecraft.client.Screenshot ''
jpk com.mojang.blaze3d.platform.NativeImage ''
jpk net.minecraft.client.Minecraft 'RenderTarget|renderTarget|Screenshot|Window|getWindow'
jpk net.minecraft.server.level.ServerLevel 'Weather|weather|Rain|Thunder'
jpk net.minecraft.world.level.Level 'Weather|weather|Rain|Thunder'
jpk net.minecraft.server.MinecraftServer 'overworld|getPlayerList|getCommands|execute'
jpk net.minecraft.commands.CommandSourceStack 'sendSystemMessage|sendSuccess|getPlayer|hasPermission|permissions'
jpk net.minecraft.commands.Commands 'LEVEL_|hasPermission|literal|argument'
jpk net.minecraft.network.FriendlyByteBuf 'writeUUID|readUUID|writeUtf|readUtf|writeBoolean|writeInt|readInt|writeBytes|readBytes|writeByte|readByte'
jpk net.minecraft.server.players.PlayerList 'getPlayer|getPlayers|isOp'
jpk net.minecraft.server.level.ServerPlayer 'sendSystemMessage|getGameProfile|permissions|getUUID'
jpk net.minecraft.resources.Identifier 'fromNamespaceAndPath'
jpk net.minecraft.client.multiplayer.ClientPacketListener 'send|getConnection'
jpk net.minecraft.client.multiplayer.ClientLevel 'class'

echo
echo "===== class name scan (weather / screenshot) ====="
printf '%s' "$CP" | tr ':' '\n' | while read -r entry; do
  [ -f "$entry" ] || continue
  case "$entry" in *.jar) unzip -l "$entry" 2>/dev/null | grep -E 'Weather|Screenshot|RenderTarget' | awk '{print $4}' | head -20 ;; esac
done | sort -u | head -40

echo
echo "===== done ====="
