#!/bin/bash

# 获取当前工作目录的绝对路径，用于创建安全的绝对路径软链接
BASE_DIR="$PWD"

# 1. 创建基础构建目录
mkdir -p "build/libs"

# ---------------------------------------------------------
# 定义一个辅助函数来安全地创建软链接并自动补全父目录
# 用法: make_link "真实目标路径" "要创建的链接位置"
# ---------------------------------------------------------
make_link() {
    local target="$1"
    local link_loc="$2"

    # 如果链接文件不存在（-e 判断实体，-L 判断断开的软链接）
    if [ ! -e "$link_loc" ] && [ ! -L "$link_loc" ]; then
        # 提取链接所在目录，并强制创建所有父级目录
        mkdir -p "$(dirname "$link_loc")"
        
        # 创建符号链接 (指向绝对路径)
        ln -s "$BASE_DIR/$target" "$link_loc"
    fi
}

# ================= 第 1 组数组处理 =================
array=(folia server_top paper spigot \
fabric_1_16_5 fabric_1_20_1 fabric_1_21 fabric_1_21_6 fabric_1_21_11 fabric_26_1 \
forge_1_7_10 forge_1_12_2 forge_1_16_5 forge_1_20_1 \
neoforge_1_21 neoforge_1_21_6 neoforge_1_21_11)

for i in "${array[@]}"; do
    make_link "core" "$i/src/main/java/com/coloryr/allmusic/server/core"
    make_link "netapi" "$i/src/main/java/com/coloryr/allmusic/server/netapi"
    make_link "client/codec" "$i/src/main/java/com/coloryr/allmusic/codec"
    
    if [ ! -d "$i/build" ]; then
        mkdir -p "$i/build"
        make_link "build/libs" "$i/build/libs"
    fi
done

# ================= 第 2 组数组处理 =================
array1=(folia server_top \
fabric_1_16_5 fabric_1_20_1 fabric_1_21 fabric_1_21_6 fabric_1_21_11 fabric_26_1 \
forge_1_7_10 forge_1_12_2 forge_1_16_5 forge_1_20_1 \
neoforge_1_21 neoforge_1_21_6 neoforge_1_21_11)

for i in "${array1[@]}"; do
    make_link "client/buffercodec" "$i/src/main/java/com/coloryr/allmusic/buffercodec"
done

# ================= 第 3 组数组处理 (onejar) =================
array3=(fabric_1_16_5 fabric_1_20_1 fabric_1_21 fabric_1_21_6 fabric_1_21_11 \
neoforge_1_21 neoforge_1_21_6 neoforge_1_21_11)

for i in "${array3[@]}"; do
    # 提前创建必要的特定目录
    mkdir -p "onejar/$i/build"
    mkdir -p "onejar/$i/src/main/java/com/coloryr/allmusic"
    mkdir -p "onejar/$i/src/main/resources/com/coloryr/allmusic/client/core/player/decoder"

    make_link "build/libs" "onejar/$i/build/libs"

    make_link "$i/src/main/java/com/coloryr/allmusic/server" "onejar/$i/src/main/java/com/coloryr/allmusic/server"
    make_link "client/$i/src/main/java/com/coloryr/allmusic/client" "onejar/$i/src/main/java/com/coloryr/allmusic/client"

    make_link "client/codec" "onejar/$i/src/main/java/com/coloryr/allmusic/codec"
    make_link "client/buffercodec" "onejar/$i/src/main/java/com/coloryr/allmusic/buffercodec"

    make_link "core" "onejar/$i/src/main/java/com/coloryr/allmusic/server/core"
    make_link "client/core" "onejar/$i/src/main/java/com/coloryr/allmusic/client/core"

    make_link "client/mp3" "onejar/$i/src/main/resources/com/coloryr/allmusic/client/core/player/decoder/mp3"
done

# ================= 第 4 组数组处理 (comm) =================
array4=(fabric_1_21 fabric_1_21_6 fabric_1_21_11 \
neoforge_1_21 neoforge_1_21_6 neoforge_1_21_11)

for i in "${array4[@]}"; do
    make_link "client/$i/src/main/java/com/coloryr/allmusic/comm" "$i/src/main/java/com/coloryr/allmusic/comm"
    make_link "client/$i/src/main/java/com/coloryr/allmusic/comm" "onejar/$i/src/main/java/com/coloryr/allmusic/comm"
done
