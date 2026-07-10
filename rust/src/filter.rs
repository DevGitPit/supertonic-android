// ============================================================================
// Audio Processing Filters (Low-Pass, High-Shelf, De-Esser)
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

    pub fn new_high_shelf(sample_rate: f32, cutoff: f32, gain_db: f32) -> Self {
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
    envelope: f32,
    threshold: f32,
    attack_coeff: f32,
    release_coeff: f32,
}

impl DeEsser {
    pub fn new(sample_rate: f32, threshold: f32, center_freq: f32) -> Self {
        let attack_ms = 2.0;
        let release_ms = 50.0;
        
        // Compute smoothing coefficients (1-pole IIR coefficient)
        let attack_coeff = 1.0 - (-1.0 / (sample_rate * attack_ms / 1000.0)).exp();
        let release_coeff = 1.0 - (-1.0 / (sample_rate * release_ms / 1000.0)).exp();

        Self {
            bandpass: BiquadFilter::new_band_pass(sample_rate, center_freq, 1.0),
            envelope: 0.0,
            threshold,
            attack_coeff,
            release_coeff,
        }
    }

    #[inline]
    pub fn process(&mut self, input: f32) -> f32 {
        // Detect sibilance band energy
        let bandpassed = self.bandpass.process(input);
        let rect = bandpassed.abs();

        // Smooth with fast attack / slow release envelope
        let coeff = if rect > self.envelope {
            self.attack_coeff
        } else {
            self.release_coeff
        };
        self.envelope = self.envelope + coeff * (rect - self.envelope);

        // Apply attenuation if above threshold
        let mut gain = 1.0;
        if self.envelope > self.threshold {
            let over = self.envelope - self.threshold;
            // Compress with a ratio factor (sensitivity = 40.0)
            gain = 1.0 / (1.0 + 40.0 * over);
            // Limit maximum attenuation to -16.5 dB (0.15)
            if gain < 0.15 {
                gain = 0.15;
            }
        }

        input * gain
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
                // De-esser: Center frequency 5500 Hz, threshold 0.012
                Self::DeEsser(DeEsser::new(sample_rate, 0.012, 5500.0))
            }
            2 => {
                // High shelf: Cutoff 4500 Hz, -7.0 dB attenuation
                Self::HighShelf(BiquadFilter::new_high_shelf(sample_rate, 4500.0, -7.0))
            }
            3 => {
                // Gentle low pass: Cutoff 6000 Hz
                Self::LowPass(BiquadFilter::new_low_pass(sample_rate, 6000.0))
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
