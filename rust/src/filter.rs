// ============================================================================
// Audio Processing Filters (Low-Pass, High-Shelf, De-Esser, Noise Gate)
// ============================================================================

#[derive(Debug, Clone)]
pub struct BiquadFilter {
    b0: f32,
    b1: f32,
    b2: f32,
    a1: f32,
    a2: f32,
    x1: f32,
    x2: f32,
    y1: f32,
    y2: f32,
}

impl BiquadFilter {
    pub fn new_low_pass(sample_rate: f32, cutoff: f32) -> Self {
        // Clamp cutoff to prevent instability or division by zero near or above Nyquist frequency
        let cutoff = cutoff.clamp(1.0, sample_rate * 0.49);
        // Butterworth low-pass filter (Q = 0.7071)
        let w0 = 2.0 * std::f32::consts::PI * cutoff / sample_rate;
        let sin_w0 = w0.sin();
        let cos_w0 = w0.cos();
        let alpha = sin_w0 / 1.41421356; // Q = 0.7071

        let b0 = (1.0 - cos_w0) / 2.0;
        let b1 = 1.0 - cos_w0;
        let b2 = (1.0 - cos_w0) / 2.0;
        let a0 = 1.0 + alpha;
        let a1 = -2.0 * cos_w0;
        let a2 = 1.0 - alpha;

        Self {
            b0: b0 / a0,
            b1: b1 / a0,
            b2: b2 / a0,
            a1: a1 / a0,
            a2: a2 / a0,
            x1: 0.0,
            x2: 0.0,
            y1: 0.0,
            y2: 0.0,
        }
    }

    pub fn new_high_pass(sample_rate: f32, cutoff: f32) -> Self {
        // Clamp cutoff to prevent instability or division by zero near or above Nyquist frequency
        let cutoff = cutoff.clamp(1.0, sample_rate * 0.49);
        // Butterworth high-pass filter (Q = 0.7071)
        let w0 = 2.0 * std::f32::consts::PI * cutoff / sample_rate;
        let sin_w0 = w0.sin();
        let cos_w0 = w0.cos();
        let alpha = sin_w0 / 1.41421356; // Q = 0.7071

        let b0 = (1.0 + cos_w0) / 2.0;
        let b1 = -(1.0 + cos_w0);
        let b2 = (1.0 + cos_w0) / 2.0;
        let a0 = 1.0 + alpha;
        let a1 = -2.0 * cos_w0;
        let a2 = 1.0 - alpha;

        Self {
            b0: b0 / a0,
            b1: b1 / a0,
            b2: b2 / a0,
            a1: a1 / a0,
            a2: a2 / a0,
            x1: 0.0,
            x2: 0.0,
            y1: 0.0,
            y2: 0.0,
        }
    }

    pub fn new_high_shelf(sample_rate: f32, cutoff: f32, gain_db: f32) -> Self {
        // Clamp cutoff to prevent instability or division by zero near or above Nyquist frequency
        let cutoff = cutoff.clamp(1.0, sample_rate * 0.49);
        let a_db = 10.0f32.powf(gain_db / 40.0);
        let w0 = 2.0 * std::f32::consts::PI * cutoff / sample_rate;
        let sin_w0 = w0.sin();
        let cos_w0 = w0.cos();
        let alpha = sin_w0 * 0.70710678; // Q = 0.7071 (S = 1.0)
        let two_sqrt_a_alpha = 2.0 * a_db.sqrt() * alpha;

        let b0 = a_db * ((a_db + 1.0) + (a_db - 1.0) * cos_w0 + two_sqrt_a_alpha);
        let b1 = -2.0 * a_db * ((a_db - 1.0) + (a_db + 1.0) * cos_w0);
        let b2 = a_db * ((a_db + 1.0) + (a_db - 1.0) * cos_w0 - two_sqrt_a_alpha);
        let a0 = (a_db + 1.0) - (a_db - 1.0) * cos_w0 + two_sqrt_a_alpha;
        let a1 = 2.0 * ((a_db - 1.0) - (a_db + 1.0) * cos_w0);
        let a2 = (a_db + 1.0) - (a_db - 1.0) * cos_w0 - two_sqrt_a_alpha;

        Self {
            b0: b0 / a0,
            b1: b1 / a0,
            b2: b2 / a0,
            a1: a1 / a0,
            a2: a2 / a0,
            x1: 0.0,
            x2: 0.0,
            y1: 0.0,
            y2: 0.0,
        }
    }

    pub fn new_band_pass(sample_rate: f32, center_freq: f32, q: f32) -> Self {
        // Clamp center_freq to prevent instability or division by zero near or above Nyquist frequency
        let center_freq = center_freq.clamp(1.0, sample_rate * 0.49);
        let w0 = 2.0 * std::f32::consts::PI * center_freq / sample_rate;
        let sin_w0 = w0.sin();
        let cos_w0 = w0.cos();
        let alpha = sin_w0 / (2.0 * q);

        let b0 = alpha;
        let b1 = 0.0;
        let b2 = -alpha;
        let a0 = 1.0 + alpha;
        let a1 = -2.0 * cos_w0;
        let a2 = 1.0 - alpha;

        Self {
            b0: b0 / a0,
            b1: b1 / a0,
            b2: b2 / a0,
            a1: a1 / a0,
            a2: a2 / a0,
            x1: 0.0,
            x2: 0.0,
            y1: 0.0,
            y2: 0.0,
        }
    }

    #[inline]
    pub fn process(&mut self, input: f32) -> f32 {
        let output = self.b0 * input + self.b1 * self.x1 + self.b2 * self.x2 - self.a1 * self.y1 - self.a2 * self.y2;
        self.x2 = self.x1;
        self.x1 = input;
        self.y2 = self.y1;
        self.y1 = output;
        output
    }
}

#[derive(Debug, Clone)]
pub struct DeEsser {
    bandpass: BiquadFilter,
    highpass: BiquadFilter,
    envelope: f32,
    threshold: f32,
    attack_coeff: f32,
    release_coeff: f32,
    sensitivity: f32,
    max_attenuation: f32,
}

impl DeEsser {
    pub fn new(sample_rate: f32, threshold: f32, center_freq: f32, sensitivity: f32, max_attenuation: f32) -> Self {
        let attack_ms = 2.0;
        let release_ms = 50.0;
        
        // Compute smoothing coefficients (1-pole IIR coefficient)
        let attack_coeff = 1.0 - (-1.0 / (sample_rate * attack_ms / 1000.0)).exp();
        let release_coeff = 1.0 - (-1.0 / (sample_rate * release_ms / 1000.0)).exp();

        Self {
            bandpass: BiquadFilter::new_band_pass(sample_rate, center_freq, 1.0),
            // Highpass split cutoff set to 4500 Hz to isolate sibilant frequencies
            highpass: BiquadFilter::new_high_pass(sample_rate, 4500.0),
            envelope: 0.0,
            threshold,
            attack_coeff,
            release_coeff,
            sensitivity,
            max_attenuation,
        }
    }

    #[inline]
    pub fn process(&mut self, input: f32) -> f32 {
        // 1. Detect sibilance band energy
        let bandpassed = self.bandpass.process(input);
        let rect = bandpassed.abs();

        // Smooth with fast attack / slow release envelope
        let coeff = if rect > self.envelope {
            self.attack_coeff
        } else {
            self.release_coeff
        };
        self.envelope = self.envelope + coeff * (rect - self.envelope);

        // 2. Determine gain reduction
        let mut gain = 1.0;
        if self.envelope > self.threshold {
            let over = self.envelope - self.threshold;
            gain = 1.0 / (1.0 + self.sensitivity * over);
            if gain < self.max_attenuation {
                gain = self.max_attenuation;
            }
        }

        // 3. Apply split-band compression:
        // Isolate sibilant/treble frequencies (above 4.5 kHz)
        let high_band = self.highpass.process(input);

        // Subtract high band from full signal (gets low/mid band), sum back high band compressed
        // output = (input - high_band) + (high_band * gain)
        // output = input - high_band * (1.0 - gain)
        input - high_band * (1.0 - gain)
    }
}

#[derive(Debug, Clone)]
pub struct NoiseGate {
    threshold: f32,
    envelope: f32,
    current_gain: f32,
    hold_samples: usize,
    hold_count: usize,
    attack_coeff: f32,
    release_coeff: f32,
    env_decay: f32,
}

impl NoiseGate {
    pub fn new(sample_rate: f32, threshold: f32) -> Self {
        let attack_ms = 3.0;   // Fast fade-in to prevent clipping quiet consonants
        let release_ms = 80.0; // Slower fade-out to preserve word endings
        let hold_ms = 30.0;    // Hold open for 30ms to prevent chatter/clipping decays
        
        let attack_coeff = 1.0 - (-1.0 / (sample_rate * attack_ms / 1000.0)).exp();
        let release_coeff = 1.0 - (-1.0 / (sample_rate * release_ms / 1000.0)).exp();
        let hold_count = (sample_rate * hold_ms / 1000.0) as usize;
        
        // Envelope decay coefficient (fast 5ms decay for level detection)
        let env_decay = (-1.0 / (sample_rate * 5.0 / 1000.0)).exp();

        Self {
            threshold,
            envelope: 0.0,
            current_gain: 1.0,
            hold_samples: 0,
            hold_count,
            attack_coeff,
            release_coeff,
            env_decay,
        }
    }

    #[inline]
    pub fn process(&mut self, input: f32) -> f32 {
        let rect = input.abs();
        
        // Peak detector with exponential decay
        if rect > self.envelope {
            self.envelope = rect;
        } else {
            self.envelope = rect + self.env_decay * (self.envelope - rect);
        }

        // Determine target gate state
        let target_gain = if self.envelope >= self.threshold {
            self.hold_samples = self.hold_count; // Reset hold timer
            1.0
        } else if self.hold_samples > 0 {
            self.hold_samples -= 1;
            1.0
        } else {
            0.0
        };

        // Smooth gain transition to prevent clicks/pumping
        let coeff = if target_gain > self.current_gain {
            self.attack_coeff
        } else {
            self.release_coeff
        };
        self.current_gain = self.current_gain + coeff * (target_gain - self.current_gain);

        input * self.current_gain
    }
}

#[derive(Debug, Clone)]
pub enum AudioFilter {
    None,
    DeEsser(DeEsser),
    HighShelf(BiquadFilter),
    LowPass(BiquadFilter),
}

impl AudioFilter {
    pub fn new(mode: i32, sample_rate: f32) -> Self {
        match mode {
            1 => {
                // Split-band De-esser: Center 6000 Hz, threshold 0.015, sensitivity 30.0, max -12dB (0.25)
                Self::DeEsser(DeEsser::new(sample_rate, 0.015, 6000.0, 30.0, 0.25))
            }
            2 => {
                // High shelf: Cutoff 5500 Hz, -3.5 dB attenuation (gentle damping)
                Self::HighShelf(BiquadFilter::new_high_shelf(sample_rate, 5500.0, -3.5))
            }
            3 => {
                // Gentle low pass: Cutoff 8000 Hz (rolls off non-speech hiss)
                Self::LowPass(BiquadFilter::new_low_pass(sample_rate, 8000.0))
            }
            4 => {
                // Aggressive Split-band De-esser: Center 5800 Hz, threshold 0.008, sensitivity 150.0, max -30dB (0.03)
                // Reductions by ~2-3 magnitudes (a factor of 10 to 30 in amplitude) restricted ONLY to sibilant frequencies.
                Self::DeEsser(DeEsser::new(sample_rate, 0.008, 5800.0, 150.0, 0.03))
            }
            _ => Self::None,
        }
    }

    #[inline]
    pub fn process(&mut self, sample: f32) -> f32 {
        match self {
            Self::None => sample,
            Self::DeEsser(d) => d.process(sample),
            Self::HighShelf(f) => f.process(sample),
            Self::LowPass(f) => f.process(sample),
        }
    }
}

#[derive(Debug, Clone)]
pub struct FilterPipeline {
    gate: Option<NoiseGate>,
    filter: AudioFilter,
}

impl FilterPipeline {
    pub fn new(mode: i32, mut sample_rate: f32) -> Self {
        // Ensure sample_rate is positive and valid to prevent division by zero or negative exponents
        if sample_rate <= 0.0 {
            sample_rate = 24000.0;
        }

        // Enable the noise gate if sibilance reduction mode is active (not Off)
        // Set threshold at -46dB (0.005)
        let gate = if mode != 0 {
            Some(NoiseGate::new(sample_rate, 0.005))
        } else {
            None
        };

        let filter = AudioFilter::new(mode, sample_rate);

        Self { gate, filter }
    }

    #[inline]
    pub fn process(&mut self, sample: f32) -> f32 {
        let filtered = self.filter.process(sample);
        if let Some(ref mut g) = self.gate {
            g.process(filtered)
        } else {
            filtered
        }
    }
}
