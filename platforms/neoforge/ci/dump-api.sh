#!/usr/bin/env bash
# mcai NeoForge 26.x API 自查脚本。
#
# 构建（不管成功失败）之后跑，把这次要核对的 NeoForge / Minecraft 类打到 Actions 日志里：
#   1) 找出 build/moddev/artifacts 与 Gradle 缓存里的 sources jar，解开到 /tmp/neo-src；
#   2) 打印关键类的源码片段（有源码时）；
#   3) 用 javap 打印关键类的公开签名（没源码时兜底）；
#   4) grep 一批会变的方法名，确认 26.x 到底叫什么。
#
# 只在 CI 里跑，不影响产物。
set -uo pipefail

echo "===== env ====="
java -version 2>&1 | head -3
./gradlew --version 2>&1 | head -14 || true

echo
echo "===== build/moddev/artifacts ====="
ls -la build/moddev/artifacts 2>/dev/null || echo '(no build/moddev/artifacts)'

echo
echo "===== jars under build/ ====="
find build -name '*.jar' -printf '%s\t%p\n' 2>/dev/null | sort -n | head -30 || echo '(none)'

echo
echo "===== neoforge/minecraft jars in gradle cache ====="
find "$HOME/.gradle/caches/modules-2/files-2.1" -name '*.jar' -printf '%s\t%p\n' 2>/dev/null \
  | grep -Ei 'neoforge|neoform|minecraft' | sort -n | head -30 || true

rm -rf /tmp/neo-src
mkdir -p /tmp/neo-src
SRC_JARS=$(find build/moddev/artifacts "$HOME/.gradle/caches/modules-2/files-2.1" -name '*sources*.jar' 2>/dev/null | tr '\n' ' ')
echo
echo "===== sources jars ====="
echo "${SRC_JARS:-(none found)}"
if [ -n "${SRC_JARS// /}" ]; then
  for j in $SRC_JARS; do
    echo "-- extracting $j"
    unzip -qo "$j" -d /tmp/neo-src 2>/dev/null
  done
  echo "extracted java files: $(find /tmp/neo-src -name '*.java' | wc -l)"
fi

show() {
  local p="/tmp/neo-src/$1" n="${2:-120}"
  echo
  echo "########## SOURCE $1"
  if [ -f "$p" ]; then
    sed -n "1,${n}p" "$p"
  else
    echo "(source not found)"
  fi
}

g() {
  echo
  echo "########## GREP $1"
  grep -rn --include='*.java' -m2 -E "$1" /tmp/neo-src 2>/dev/null | head -10 || true
}

show net/minecraft/network/protocol/common/custom/CustomPacketPayload.java 90
show net/neoforged/neoforge/network/event/RegisterPayloadHandlersEvent.java 90
show net/neoforged/neoforge/network/registration/PayloadRegistrar.java 160
show net/neoforged/neoforge/network/registration/IPayloadContext.java 80
show net/neoforged/neoforge/network/PacketDistributor.java 90
show net/neoforged/neoforge/event/tick/ServerTickEvent.java 90
show net/neoforged/neoforge/client/event/ClientPlayerNetworkEvent.java 90
show net/neoforged/neoforge/event/RegisterCommandsEvent.java 60
show net/neoforged/fml/loading/FMLPaths.java 60
show net/neoforged/fml/common/EventBusSubscriber.java 60
show net/minecraft/client/Screenshot.java 90

g 'fromNamespaceAndPath'
g 'class Identifier'
g 'class ResourceLocation'
g 'class NativeImage'
g 'takeScreenshot\('
g 'getMainRenderTarget\('
g 'asByteArray\('
g 'downloadTexture\('
g 'setWeatherParameters'
g 'record ServerTickEvent|class ServerTickEvent'
g 'class ServerStartedEvent|class ServerStoppingEvent'
g 'class PlayerLoggedOutEvent'
g 'void sendSystemMessage'
g 'hasPermissions\('
g 'public int getPlayerCount|getPlayerCount\(\)'
g 'List<ServerPlayer> getPlayers'
g 'enqueueWork'
g 'playToServer|playToClient|playBidirectional'
g 'interface IPayloadContext'
g 'class ServerboundCustomPayloadPacket|class ClientboundCustomPayloadPacket'
g 'getGameProfile'

echo
echo "===== javap fallback ====="
CP=""
for j in $(find build/moddev/artifacts "$HOME/.gradle/caches/modules-2/files-2.1" -name '*.jar' 2>/dev/null | grep -v sources | head -60); do
  CP="$CP:$j"
done
jp() {
  echo
  echo "########## JAVAP $1"
  javap -classpath "$CP" "$1" 2>&1 | head -70
}
jp net.minecraft.network.protocol.common.custom.CustomPacketPayload
jp net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent
jp net.neoforged.neoforge.network.registration.PayloadRegistrar
jp net.neoforged.neoforge.network.registration.IPayloadContext
jp net.neoforged.neoforge.network.PacketDistributor
jp net.neoforged.neoforge.event.tick.ServerTickEvent
jp net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent
jp net.neoforged.neoforge.event.RegisterCommandsEvent
jp net.neoforged.fml.loading.FMLPaths
jp net.neoforged.fml.loading.FMLEnvironment
jp net.neoforged.fml.common.EventBusSubscriber

echo
echo "===== done ====="
