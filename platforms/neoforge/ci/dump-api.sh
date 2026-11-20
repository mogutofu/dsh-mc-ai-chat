#!/usr/bin/env bash
# mcai NeoForge 26.x API 自查脚本。
#
# 构建（不管成功失败）之后跑，把这次要核对的 NeoForge / Minecraft 类打到 Actions 日志里：
#   1) 找出 build/moddev/artifacts 与 Gradle 缓存里的 sources jar，解开到 /tmp/neo-src；
#   2) 打印关键 NeoForge 类的源码片段（有源码时）；
#   3) 用 javap 打印 Minecraft / FML 关键方法的签名（MC 没有源码，只能看签名）。
#
# 只在 CI 里跑，不影响产物。
set -uo pipefail

echo "===== env ====="
java -version 2>&1 | head -3
[ -x ./gradlew ] && ./gradlew --version 2>&1 | head -14 || echo '(gradlew not executable)'

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

show net/minecraft/network/protocol/common/custom/CustomPacketPayload.java 70
show net/neoforged/neoforge/network/event/RegisterPayloadHandlersEvent.java 60
show net/neoforged/neoforge/network/registration/PayloadRegistrar.java 130
show net/neoforged/neoforge/network/handling/IPayloadContext.java 120
show net/neoforged/neoforge/client/event/ClientPlayerNetworkEvent.java 90
show net/minecraft/client/Screenshot.java 90

# MC 没有源码，只能看编译产物签名
CP=""
for j in $(find build/moddev/artifacts "$HOME/.gradle/caches/modules-2/files-2.1" -name '*.jar' 2>/dev/null | grep -v sources | head -80); do
  CP="$CP:$j"
done
jpk() {
  echo
  echo "########## $1 | $2"
  javap -classpath "$CP" "$1" 2>/dev/null | grep -E "$2" | head -25 || echo '  (no match / class missing)'
}

jpk net.minecraft.commands.CommandSourceStack 'sendSystemMessage|sendSuccess|getPlayer|hasPermission|permissions'
jpk net.minecraft.commands.Commands 'LEVEL_|hasPermission|literal|argument'
jpk net.minecraft.network.FriendlyByteBuf 'writeUUID|readUUID|writeUtf|readUtf|writeByteArray|readByteArray|writeBoolean|writeInt|readInt|readBytes|writeBytes|writeByte|readByte'
jpk net.minecraft.server.level.ServerLevel 'setWeather|rainLevel|thunderLevel'
jpk net.minecraft.server.MinecraftServer 'overworld|getPlayerList|getCommands'
jpk net.minecraft.server.players.PlayerList 'getPlayer|getPlayers|isOp'
jpk net.minecraft.server.level.ServerPlayer 'sendSystemMessage|getGameProfile|permissions|getUUID'
jpk net.minecraft.client.Screenshot 'takeScreenshot'
jpk net.minecraft.client.Minecraft 'getMainRenderTarget|getConnection'
jpk com.mojang.blaze3d.platform.NativeImage 'asByteArray|downloadTexture|flipY'
jpk net.minecraft.resources.Identifier 'fromNamespaceAndPath'
jpk net.minecraft.network.protocol.common.custom.CustomPacketPayload 'Type|codec'
jpk net.minecraft.network.codec.StreamCodec 'of\('
jpk net.minecraft.network.chat.Component 'literal|withStyle'
jpk net.minecraft.ChatFormatting 'GOLD|AQUA|RED|GRAY'
jpk net.neoforged.neoforge.network.handling.IPayloadContext 'player|enqueueWork'
jpk net.neoforged.neoforge.network.PacketDistributor 'sendToPlayer'
jpk net.neoforged.neoforge.client.network.ClientPacketDistributor 'sendToServer'
jpk net.neoforged.fml.loading.FMLPaths 'GAMEDIR|CONFIGDIR'
jpk net.neoforged.fml.loading.FMLEnvironment 'getDist|Dist'
jpk net.neoforged.fml.common.EventBusSubscriber 'modid|value'
jpk com.mojang.authlib.GameProfile 'name|getName'

echo
echo "===== done ====="
