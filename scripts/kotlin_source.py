"""Lightweight Kotlin lexical masking for repository validation scripts."""
from __future__ import annotations


def mask_kotlin_noise(source: str) -> str:
    """Mask comments and string/character contents while preserving offsets and newlines."""
    output = list(source)
    length = len(source)

    def blank(start: int, end: int) -> None:
        for position in range(start, end):
            if source[position] not in "\r\n":
                output[position] = " "

    index = 0
    while index < length:
        if source.startswith("//", index):
            end = source.find("\n", index)
            if end < 0:
                end = length
            blank(index, end)
            index = end
        elif source.startswith("/*", index):
            start = index
            depth = 1
            index += 2
            while index < length and depth:
                if source.startswith("/*", index):
                    depth += 1
                    index += 2
                elif source.startswith("*/", index):
                    depth -= 1
                    index += 2
                else:
                    index += 1
            blank(start, index)
        elif source.startswith('"""', index):
            start = index
            index += 3
            while index < length and not source.startswith('"""', index):
                index += 1
            if index < length:
                index += 3
            blank(start, index)
        elif source[index] == '"':
            start = index
            index += 1
            while index < length:
                if source[index] == "\\":
                    index = min(index + 2, length)
                elif source[index] == '"':
                    index += 1
                    break
                else:
                    index += 1
            blank(start, index)
        elif source[index] == "'":
            start = index
            index += 1
            while index < length:
                if source[index] == "\\":
                    index = min(index + 2, length)
                elif source[index] == "'":
                    index += 1
                    break
                else:
                    index += 1
            blank(start, index)
        else:
            index += 1
    return "".join(output)
