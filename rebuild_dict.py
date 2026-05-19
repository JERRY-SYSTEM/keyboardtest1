#!/usr/bin/env python3
"""
Rebuild pinyin_dict.json with correct char ordering per pinyin.
Strategy: for each pinyin, define the correct order of common chars.
"""
import json
import os
import sys
import re
from collections import defaultdict

# 每个拼音的常用字正确顺序（只列出需要特别排序的拼音）
PINYIN_CHAR_ORDER = {
    'yi': '一衣医依仪宜姨移遗疑乙已以矣蚁椅亿忆义艺议亦异役译易疫益谊意毅翼因阴音银引饮隐印应英樱鹰迎盈营蝇赢影映硬',
    'de': '的地得德底',
    'shi': '是时事十世市识实使始士氏示式势视试室适逝释施师诗石时拾食史',
    'you': '有又由友游右油优忧幽悠尤邮幼诱',
    'wo': '我握卧沃涡倭',
    'ta': '他她它塔',
    'zai': '在再载',
    'le': '了乐勒',
    'bu': '不部布步',
    'ren': '人任认',
    'ge': '个各革',
    'dou': '都斗豆',
    'lai': '来赖',
    'shang': '上商',
    'dao': '到道导',
    'shuo': '说',
    'yao': '要',
    'hui': '会回',
    'hao': '好号',
    'xue': '学',
    'jia': '家加',
    'xin': '新心',
    'hua': '化话',
    'guo': '国',
    'nian': '年',
    'chu': '出处',
    'shen': '什身',
    'zuo': '作做',
    'ri': '日',
    'ye': '也业',
    'mei': '美每',
    'wan': '万完',
    'zhang': '长张',
    'fang': '方',
    'qian': '前千',
    'hou': '后',
    'zhong': '中重',
    'xiao': '小',
    'wen': '文问',
    'ti': '体',
    'li': '里力',
    'zhe': '这着',
    'na': '那哪',
    'ne': '呢',
    'ma': '吗',
    'ba': '吧把',
    'a': '啊',
    'kai': '开',
}

def parse_rime_dict(input_file):
    pinyin_map = defaultdict(list)
    header_end = False
    with open(input_file, 'r', encoding='utf-8') as f:
        for line in f:
            line = line.strip()
            if not line or line.startswith('#'):
                continue
            if line == '...':
                header_end = True
                continue
            if not header_end:
                continue
            parts = line.split('\t')
            if len(parts) < 2:
                continue
            char = parts[0]
            pinyin_str = parts[1]
            weight_str = parts[2] if len(parts) > 2 else "0"
            weight_str = weight_str.replace('%', '')
            try:
                weight = int(float(weight_str))
            except ValueError:
                weight = 0
            if len(char) != 1:
                continue
            pinyin_clean = ''.join(c for c in pinyin_str if not c.isdigit())
            pinyin_map[pinyin_clean].append((weight, char))
    return pinyin_map

def sort_chars(pinyin, chars_with_weights):
    """Sort chars: use predefined order if available, otherwise by weight"""
    if pinyin in PINYIN_CHAR_ORDER:
        order = PINYIN_CHAR_ORDER[pinyin]
        # Sort by position in order string
        def sort_key(item):
            weight, char = item
            if char in order:
                return (0, order.index(char), '')
            else:
                return (1, -weight, char)
        chars_with_weights.sort(key=sort_key)
    else:
        # Default: sort by weight descending
        chars_with_weights.sort(key=lambda x: -x[0])
    return ''.join(c[1] for c in chars_with_weights)

def main():
    input_file = sys.argv[1] if len(sys.argv) > 1 else '/tmp/luna_pinyin.dict.yaml'
    output_file = sys.argv[2] if len(sys.argv) > 2 else 'app/src/main/assets/pinyin_dict.json'
    print(f"Rebuilding {output_file}")
    raw = parse_rime_dict(input_file)
    result = {}
    for pinyin, chars in raw.items():
        result[pinyin] = sort_chars(pinyin, chars)
    os.makedirs(os.path.dirname(output_file), exist_ok=True)
    with open(output_file, 'w', encoding='utf-8') as f:
        json.dump(result, f, ensure_ascii=False, separators=(',', ':'))
    total = sum(len(v) for v in result.values())
    print(f"Done: {len(result)} keys, {total} chars, {os.path.getsize(output_file)} bytes")
    # Verify
    for p in ['yi', 'de', 'shi', 'kai', 'ye', 'wo', 'ta', 'zai', 'le', 'bu', 'ren', 'ge', 'dou', 'lai', 'shang', 'dao', 'shuo', 'yao', 'hui', 'hao', 'xue', 'jia', 'xin', 'hua', 'guo', 'nian', 'chu', 'shen', 'zuo', 'ri', 'ye', 'mei', 'wan', 'zhang', 'fang', 'qian', 'hou', 'zhong', 'xiao', 'wen', 'ti', 'li', 'zhe', 'na', 'ne', 'ma', 'ba', 'a', 'you']:
        chars = result.get(p, '')
        print(f"  {p}: {chars[:12]}")

if __name__ == '__main__':
    main()
