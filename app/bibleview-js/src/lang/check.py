import yaml
import re
import glob
import os
from dataclasses import dataclass


placeholder_regex = r"%s"
dir = os.path.dirname(os.path.realpath(__file__))
default_file = os.path.join(dir, "default.yaml")

def read_file(filename):
    with open(filename) as f:
        return yaml.safe_load(f)

def count_placeholders(filename):
    strings_dict = read_file(filename)
    counts = {}
    for k,v in strings_dict.items():
        if v == "":
            continue
        matches = re.findall(placeholder_regex, v)
        # Store the number of occurences of the placeholder regex in check_dict
        counts[k] = len(matches)
    return counts


@dataclass
class CountsNotMatch(Exception):
    string: str
    count: int
    target_count: int
    def __str__(self):
        return f"Error in {filename}: {self.string} contains {self.count} placeholders, while it should contain {self.target_count} placeholders"


def check_counts(counts, default_counts):
    for string, count in counts.items():
        target_count = default_counts[string]
        if target_count != count:
            raise CountsNotMatch(string, count, target_count)

default_counts = count_placeholders(default_file)

for filename in glob.glob(os.path.join(dir, "*.yaml")):
    if filename == default_file:
        continue
    counts = count_placeholders(filename)
    check_counts(counts, default_counts)

