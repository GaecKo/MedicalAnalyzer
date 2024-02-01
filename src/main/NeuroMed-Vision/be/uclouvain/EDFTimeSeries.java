/**
 * Copyright (c) 2022, Sebastien Jodogne, ICTEAM UCLouvain, Belgium
 *
 * Permission is hereby granted, free of charge, to any person
 * obtaining a copy of this software and associated documentation
 * files (the "Software"), to deal in the Software without
 * restriction, including without limitation the rights to use, copy,
 * modify, merge, publish, distribute, sublicense, and/or sell copies
 * of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS
 * BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN
 * ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 **/


package java.be.uclouvain;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;


/**
 * Class to access the content of an EDF (European Data Format) file.
 * The implementation is as close as possible to the internal EDF file format.
 */
public class EDFTimeSeries {
    static private class Reader {
        private final byte[] data;
        private int offset = 0;

        private Reader(byte[] data) {
            this.data = data;
        }

        public byte[] readChunk(int length) {
            if (offset + length > data.length) {
                throw new IllegalArgumentException();
            } else {
                byte[] chunk = Arrays.copyOfRange(data, offset, offset + length);
                offset += length;
                return chunk;
            }
        }

        private String trim(String s) {
            int length = s.length();

            while (length > 0 &&
                    s.charAt(length - 1) == 0x20) {
                length--;
            }

            return s.substring(0, length);
        }

        public String readAscii(int length) {
            return trim(new String(readChunk(length), StandardCharsets.US_ASCII));
        }

        public int readInteger(int length) {
            return Integer.parseInt(readAscii(length));
        }

        public float readFloat(int length) {
            return Float.parseFloat(readAscii(length));
        }

        public double readDouble(int length) {
            return Double.parseDouble(readAscii(length));
        }

        public int getOffset() {
            return offset;
        }
    }


    /**
     * Class that contains the parameters of one channel (i.e. one electrode) in an EDF file.
     */
    static public class Channel {
        private String label;
        private String transducerType;
        private String physicalDimension;
        private float physicalMinimum;
        private float physicalMaximum;
        private int digitalMinimum;
        private int digitalMaximum;
        private String prefiltering;
        private int numberOfSamplesInRecord;
        private boolean physicalInitialized = false;
        private float physicalScaling;

        private Channel() {
        }

        private void readLabel(Reader reader) {
            label = reader.readAscii(16);
        }

        private void readTransducerType(Reader reader) {
            transducerType = reader.readAscii(80);
        }

        private void readPhysicalDimension(Reader reader) {
            physicalDimension = reader.readAscii(8);
        }

        private void readPhysicalMinimum(Reader reader) {
            physicalMinimum = reader.readFloat(8);
        }

        private void readPhysicalMaximum(Reader reader) {
            physicalMaximum = reader.readFloat(8);
        }

        private void readDigitalMinimum(Reader reader) {
            digitalMinimum = reader.readInteger(8);
        }

        private void readDigitalMaximum(Reader reader) {
            digitalMaximum = reader.readInteger(8);
        }

        private void readPrefiltering(Reader reader) {
            prefiltering = reader.readAscii(80);
        }

        private void readNumberOfSamplesInRecord(Reader reader) {
            numberOfSamplesInRecord = reader.readInteger(8);
        }

        /**
         * Get the label of the channel.
         * @return The label.
         */
        public String getLabel() {
            return label;
        }

        /**
         * Get the type of transducer of the channel.
         * @return The transducer type.
         */
        public String getTransducerType() {
            return transducerType;
        }

        /**
         * Get the physical units (dimension) of the channel.
         * @return The units.
         */
        public String getPhysicalDimension() {
            return physicalDimension;
        }

        /**
         * Get the minimum physical value of the channel.
         * @return The minimum value.
         */
        public float getPhysicalMinimum() {
            return physicalMinimum;
        }

        /**
         * Get the maximum physical value of the channel.
         * @return The maximum value.
         */
        public float getPhysicalMaximum() {
            return physicalMaximum;
        }

        /**
         * Get the minimum integer value encoded in the channel.
         * @return The minimum value.
         */
        public int getDigitalMinimum() {
            return digitalMinimum;
        }

        /**
         * Get the maximum integer value encoded in the channel.
         * @return The maximum value.
         */
        public int getDigitalMaximum() {
            return digitalMaximum;
        }

        /**
         * Get the parameters of the prefiltering that was applied to the channel.
         * @return The prefiltering parameters.
         */
        public String getPrefiltering() {
            return prefiltering;
        }

        /**
         * Get the number of samples in each data block for this channel.
         * @return The number of samples.
         */
        public int getNumberOfSamplesInRecord() {
            return numberOfSamplesInRecord;

        }

        /**
         * Convert from digital values to physical values for this channel.
         * @param digitalValue The digital value of interest.
         * @return The physical value.
         * @throws IOException If the file doesn't follow the EDF specification.
         */
        public float getPhysicalValue(int digitalValue) throws IOException {
            if (!physicalInitialized) {
                if (physicalMinimum >= physicalMaximum ||
                        digitalMinimum >= digitalMaximum) {
                    throw new IOException("Bad EDF file format");
                } else {
                    physicalScaling = (physicalMaximum - physicalMinimum) / (float) (digitalMaximum - digitalMinimum);
                    physicalInitialized = true;
                }
            }

            if (digitalValue < digitalMinimum ||
                    digitalValue > digitalMaximum) {
                throw new IllegalArgumentException();
            } else {
                return (float) (digitalValue - digitalMinimum) * physicalScaling + physicalMinimum;
            }
        }
    }


    /**
     * Class that contains the sub-fields of the "local recording
     * identification" field, if the EDF file complies with the EDF+
     * specification:
     * https://www.edfplus.info/specs/edfplus.html#additionalspecs
     **/
    static public class PlusLocalRecordingIdentification {
        private final String startDate;
        private final String hospitalCode;
        private final String investigator;
        private final String equipment;

        private PlusLocalRecordingIdentification(String startDate,
                                                 String hospitalCode,
                                                 String investigator,
                                                 String equipment) {
            this.startDate = startDate;
            this.hospitalCode = hospitalCode;
            this.investigator = investigator;
            this.equipment = equipment;
        }

        /**
         * Get the start date of the recording.
         * @return The start date.
         **/
        public String getStartDate() {
            return startDate;
        }

        /**
         * Get the hospital administration code of the investigation,
         * i.e. EEG number or PSG number.
         * @return The hospital code.
         **/
        public String getHospitalCode() {
            return hospitalCode;
        }

        /**
         * Get the code specifying the responsible investigator or
         * technician.
         * @return The investigator code.
         **/
        public String getInvestigator() {
            return investigator;
        }

        /**
         * Get the code specifying the used equipment. 
         * @return The used equipment.
         **/
        public String getEquipment() {
            return equipment;
        }
    }


    private final byte[] data;
    private final String localPatientIdentification;
    private final String localRecordingIdentification;
    private final String startDate;
    private final String startTime;
    private final int headerSize;
    private final int numberOfDataRecords;
    private final double durationOfDataRecord;  // In seconds
    private int recordSize;
    private final Channel[] channels;
    private final Map<String, Integer> channelsIndex = new HashMap<>();
    private final PlusLocalRecordingIdentification plusLocalRecordingIdentification;

    /**
     * Parse the provided EDF file.
     * @param data The EDF file to be parsed.
     * @throws IOException If the file doesn't follow the EDF specification.
     */
    public EDFTimeSeries(byte[] data) throws IOException {
        this.data = data;

        Reader reader = new Reader(data);

        if (!reader.readAscii(8).equals("0")) {
            throw new IOException("Bad EDF file format");
        }

        localPatientIdentification = reader.readAscii(80);
        localRecordingIdentification = reader.readAscii(80);
        startDate = reader.readAscii(8);
        startTime = reader.readAscii(8);

        headerSize = reader.readInteger(8);

        reader.readChunk(44);  // Skipping reserved

        numberOfDataRecords = reader.readInteger(8);
        durationOfDataRecord = reader.readDouble(8);

        channels = new Channel[(int) reader.readInteger(4)];
        for (int i = 0; i < channels.length; i++) {
            channels[i] = new Channel();
        }

        for (int i = 0; i < channels.length; i++) {
            Channel channel = channels[i];
            channel.readLabel(reader);
            channelsIndex.put(channel.getLabel(), i);
        }

        for (Channel channel : channels) {
            channel.readTransducerType(reader);
        }

        for (Channel channel : channels) {
            channel.readPhysicalDimension(reader);
        }

        for (Channel channel : channels) {
            channel.readPhysicalMinimum(reader);
        }

        for (Channel channel : channels) {
            channel.readPhysicalMaximum(reader);
        }

        for (Channel channel : channels) {
            channel.readDigitalMinimum(reader);
        }

        for (Channel channel : channels) {
            channel.readDigitalMaximum(reader);
        }

        for (Channel channel : channels) {
            channel.readPrefiltering(reader);
        }

        for (Channel channel : channels) {
            channel.readNumberOfSamplesInRecord(reader);
        }

        // Skipping reserved
        for (int i = 0; i < channels.length; i++) {
            reader.readChunk(32);
        }

        if (headerSize != reader.getOffset()) {
            throw new IOException("Bad EDF file format");
        }

        recordSize = 0;
        for (Channel channel : channels) {
            recordSize += 2 * channel.getNumberOfSamplesInRecord();
        }

        if (headerSize + recordSize * numberOfDataRecords != data.length) {
            throw new IOException("Bad EDF file format");
        }

        String[] split = localRecordingIdentification.split(" ");
        if (split.length == 5 &&
            split[0].equals("Startdate")) {
            plusLocalRecordingIdentification = new PlusLocalRecordingIdentification(split[1], split[2], split[3], split[4]);
        } else {
            plusLocalRecordingIdentification = null;
        }
    }

    /**
     * Parse the EDF file that is contained in some file on the filesystem.
     * @param path Path to the EDF file.
     * @return The parsed EDF file.
     * @throws IOException  If the file doesn't follow the EDF specification.
     */
    static public EDFTimeSeries parseFile(String path) throws IOException {
        // This requires Java 7
        byte[] data = Files.readAllBytes(Paths.get(path));
        return new EDFTimeSeries(data);
    }

    /**
     * Get the local patient identification field.
     * @return The local patient identification.
     */
    public String getLocalPatientIdentification() {
        return localPatientIdentification;
    }

    /**
     * Get the local recording identification field.
     * @return The local recording identification.
     */
    public String getLocalRecordingIdentification() {
        return localRecordingIdentification;
    }

    /**
     * Get the start date of the recording.
     * @return The start date.
     */
    public String getStartDate() {
        return startDate;
    }

    /**
     * Get the start time of the recording.
     * @return The start time.
     */
    public String getStartTime() {
        return startTime;
    }

    /**
     * Get the duration of the recording.
     * @return The duration, expressed in seconds.
     */
    public double getDuration() {
        return durationOfDataRecord * (double) numberOfDataRecords;
    }

    /**
     * Get the number of channels (electrodes) in this recording.
     * @return The number of channels.
     */
    public int getNumberOfChannels() {
        return channels.length;
    }

    /**
     * Access one of the channels (electrodes) in the recording using its index.
     * @param channelIndex The index of the channel of interest.
     * @return The channel (electrode).
     */
    public Channel getChannel(int channelIndex) {
        return channels[channelIndex];
    }

    /**
     * Get the sampling frequency of one channel (electrode) in the recording.
     * @param channelIndex The index of the channel of interest.
     * @return The sampling frequency.
     */
    public float getSamplingFrequency(int channelIndex) {
        return (float) ((double) getChannel(channelIndex).getNumberOfSamplesInRecord() / durationOfDataRecord);
    }

    /**
     * Get the total number of samples of one channel (electrode) in the whole recording.
     * @param channelIndex The index of the channel of interest.
     * @return The number of samples.
     */
    public int getNumberOfSamples(int channelIndex) {
        return getChannel(channelIndex).getNumberOfSamplesInRecord() * numberOfDataRecords;
    }

    private int getDigitalSample(int offset) {
        // "Each sample value is represented as a 2-byte integer
        // in 2's complement format." NB: Byte.toUnsignedInt()
        // requires Java 8.

        int value = Byte.toUnsignedInt(data[offset + 1]) * 256 + Byte.toUnsignedInt(data[offset]);
        if (value >= 32768) {
            return value - 65536;
        } else {
            return value;
        }
    }

    /**
     * Get the digital value (integer) of one sample in one of the channels (electrodes).
     * @param channelIndex The index of the channel of interest.
     * @param sample The index of the sample of interest in the recording
     *               (must be smaller than getNumberOfSamples())
     * @return The digital value.
     */
    public int getDigitalValue(int channelIndex,
                               int sample) {
        int samplesInRecord = getChannel(channelIndex).getNumberOfSamplesInRecord();
        int recordIndex = sample / samplesInRecord;
        int recordOffset = headerSize + recordIndex * recordSize;

        for (int i = 0; i < channelIndex; i++) {
            recordOffset += 2 * getChannel(i).getNumberOfSamplesInRecord();
        }

        recordOffset += 2 * (sample % samplesInRecord);

        return getDigitalSample(recordOffset);
    }

    /**
     * Access one of the channels (electrodes) in the recording using its symbolic name (label).
     * @param label The label of the channel of interest.
     * @return The channel (electrode).
     */
    public Channel lookupChannel(String label) {
        return channels[channelsIndex.get(label)];
    }

    /**
     * Access the index of some channel (electrode) in the recording using its symbolic name (label).
     * @param label The label of the channel of interest.
     * @return The index of the channel (electrode).
     */
    public int lookupChannelIndex(String label) {
        return channelsIndex.get(label);
    }

    /**
     * If the file complies with the EDF+ specification, get a parsed
     * version of the "local recording identification" field.
     * @return The parsed field, or `null` if this is not an EDF+ file.
     **/
    public PlusLocalRecordingIdentification getPlusLocalRecordingIdentification() {
        return plusLocalRecordingIdentification;
    }

    /**
     * If the file is EDF+, and if the equipment code is a list
     * delimited by ";" of key/values pairs separated by "=", look for
     * the value associated with the given key.
     *
     * NB: This is an extension used in this course!
     *
     * @param key The key of interest.
     * @return The value of the equipment parameter associated with
     * this key, or `null` if absent.
     **/
    public String lookupEquipmentParameterAsString(String key) {
        if (plusLocalRecordingIdentification != null) {
            String[] tokens = plusLocalRecordingIdentification.getEquipment().split(";");
            for (String token: tokens) {
                int pos = token.indexOf('=');
                if (pos != -1 &&
                    token.substring(0, pos).equals(key)) {
                    return token.substring(pos + 1);
                }
            }
        }
        
        return null;
    }

    /**
     * Returns the integer value of a parameter associated with the
     * equipment.
     *
     * NB: This is an extension used in this course!
     *
     * @see #lookupEquipmentParameterAsString(String)
     * @param key The key of interest.
     * @return The integer value of the equipment parameter associated
     * with this key, or `null` if absent.
     **/
    public Integer lookupEquipmentParameterAsInteger(String key) {
        String value = lookupEquipmentParameterAsString(key);
        if (value == null) {
            return null;
        } else {
            try {
                return Integer.parseInt(value);
            } catch (NumberFormatException e) {
                return null;
            }
        }
    }

    /**
     * Returns the number of epochs that are stored in this EDF+ file,
     * provided the "equipment code" sub-field in the "local recording
     * identification" field contains the "Epochs" parameter.
     *
     * NB: This is an extension used in this course!
     *
     * @see #lookupEquipmentParameterAsString(String)
     * @return The number of epochs, or `null` if absent.
     */
    public Integer lookupNumberOfEpochs() {
        return lookupEquipmentParameterAsInteger("Epochs");
    }
}
