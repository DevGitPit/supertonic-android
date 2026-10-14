use regex::Regex;
use std::sync::OnceLock;
use std::collections::BTreeMap;
use once_cell::sync::Lazy;
use serde::Deserialize;
use super::{common_preprocess, check_and_add_ending_punctuation, split_sentences_generic, LanguageNormalizer};

#[derive(Deserialize)]
struct EnglishConfig {
    replacements: BTreeMap<String, String>,
    abbreviations: Vec<String>,
}

static ENGLISH_CONFIG: Lazy<EnglishConfig> = Lazy::new(|| {
    let json_str = include_str!("configs/en.json");
    serde_json::from_str(json_str).expect("Failed to parse en.json")
});

pub struct EnglishNormalizer;

pub static ENGLISH_NORMALIZER: Lazy<EnglishNormalizer> = Lazy::new(|| EnglishNormalizer);

impl LanguageNormalizer for EnglishNormalizer {
    fn preprocess(&self, text: &str) -> String {
        let mut text = text.to_string();
        for (from, to) in &ENGLISH_CONFIG.replacements {
            text = text.replace(from, to);
        }
        
        let cleaned = common_preprocess(&text);
        check_and_add_ending_punctuation(cleaned)
    }

    fn split_sentences(&self, text: &str) -> Vec<String> {
        static ENGLISH_SENTENCE_RE: OnceLock<Regex> = OnceLock::new();
        let re = ENGLISH_SENTENCE_RE.get_or_init(|| Regex::new(r"([.!?]['\u{2019}\u{201D}\u{0022}\)\}\]]?)\s+").unwrap());
        
        let abbrev_refs: Vec<&str> = ENGLISH_CONFIG.abbreviations.iter().map(|s| s.as_str()).collect();
        split_sentences_generic(text, re, &abbrev_refs)
    }

    fn max_chunk_len(&self) -> usize {
        300
    }

    fn should_wrap_tags(&self) -> bool {
        true
    }
}
