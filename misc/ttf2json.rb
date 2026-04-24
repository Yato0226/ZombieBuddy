#!/usr/bin/env ruby
# frozen_string_literal: true
#
# TTF/OTF → PNG atlas + JSON (all cmap codepoints with glyphs, not ASCII-only).
#
# gem install freetype zpng
#
# usage:
#   ruby ttf2json.rb font.ttf out_prefix 10
# writes out_prefix.json and out_prefix_0.png
#
# Full kerning is O(n²) in the number of glyphs. If you have a huge cmap,
# set TTF2FNT_MAX_KERN_GLYPHS (default 2000) to skip kernings when n exceeds it.

require "json"
require "freetype"
require "zpng"

include FreeType::C
include ZPNG

FT_PIXEL_MODE_GRAY = 2
FT_STYLE_FLAG_BOLD   = 1 << 0
FT_STYLE_FLAG_ITALIC = 1 << 1
FT_KERNING_DEFAULT = 0

LOAD_FLAGS = FT_LOAD_RENDER | FT_LOAD_FORCE_AUTOHINT

ATLAS_PAD = 1
INITIAL_ATLAS_W = 512
INITIAL_ATLAS_H = 256

# Cmap iteration (not in older freetype gem); fallback scans scalar values.
begin
  module FreeType::C
    attach_function :FT_Get_First_Char, [:pointer, :pointer], :ulong
    attach_function :FT_Get_Next_Char, [:pointer, :ulong, :pointer], :ulong
  end
rescue StandardError
  # duplicate attach if gem adds these later
end

def cmap_codepoints(face)
  ag = FFI::MemoryPointer.new(:uint)
  list = []
  if respond_to?(:FT_Get_First_Char, true)
    cp = FT_Get_First_Char(face, ag)
    while !cp.zero?
      list << cp
      nxt = FT_Get_Next_Char(face, cp, ag)
      break if nxt == cp

      cp = nxt
    end
  end
  return list.uniq.sort if list.any?

  # Fallback: all assigned Unicode scalars (skips UTF-16 surrogates).
  out = []
  (0..0x10FFFF).each do |cp|
    next if cp >= 0xD800 && cp <= 0xDFFF

    gid = FT_Get_Char_Index(face, cp)
    out << cp unless gid.zero?
  end
  out
end

def shelf_pack!(placements, atlas_w)
  x = y = row_h = 0
  atlas_h = INITIAL_ATLAS_H

  placements.each do |g|
    pack_w = [g[:gw], 1].max
    pack_h = [g[:gh], 1].max

    loop do
      if x + pack_w + ATLAS_PAD > atlas_w
        x = 0
        y += row_h + ATLAS_PAD
        row_h = 0
      end
      if y + pack_h > atlas_h
        atlas_h *= 2
        redo
      end

      g[:atlas_x] = x
      g[:atlas_y] = y
      x += pack_w + ATLAS_PAD
      row_h = [row_h, pack_h].max
      break
    end
  end

  [atlas_w, atlas_h]
end

def blit_glyph!(img, g, ax, ay)
  gw = g[:gw]
  gh = g[:gh]
  return if gw.zero? || gh.zero?

  pitch = g[:pitch]
  buf = g[:buffer]
  pitch_abs = pitch.abs
  gh.times do |yy|
    row = pitch >= 0 ? yy : gh - 1 - yy
    gw.times do |xx|
      gray = buf.get_uint8(row * pitch_abs + xx)
      next if gray.zero?

      img[ax + xx, ay + yy] = Color.new(255, 255, 255, gray)
    end
  end
end

def trim_atlas(img, placements)
  max_r = placements.map { |g| g[:atlas_x] + [g[:gw], 1].max }.max
  max_b = placements.map { |g| g[:atlas_y] + [g[:gh], 1].max }.max
  max_r = [[max_r, 1].max, img.width].min
  max_b = [[max_b, 1].max, img.height].min

  out = Image.new(width: max_r, height: max_b)
  max_b.times do |yy|
    max_r.times do |xx|
      out[xx, yy] = img[xx, yy]
    end
  end
  out
end

# Pretty atlas + face (standard JSON indent); glyphs / kernings = one object per line
# with spaces after colons (collapsed from JSON.pretty_generate per row).
def format_font_json(doc)
  head = JSON.pretty_generate("atlas" => doc["atlas"], "face" => doc["face"])
  lines = head.lines(chomp: true)
  lines.pop # drop closing `}` of the temporary root object

  buf = lines.join("\n")
  buf << ",\n"
  buf << format_json_object_array_section("glyphs", doc["glyphs"])
  buf << ",\n"
  buf << format_json_object_array_section("kernings", doc["kernings"])
  buf << "\n}\n"
  buf
end

def format_json_object_array_section(key, rows)
  return %(  "#{key}": []) if rows.empty?

  buf = +%(  "#{key}": [\n)
  rows.each_with_index do |row, i|
    buf << "    #{row.to_json}"
    buf << (i < rows.size - 1 ? ",\n" : "\n")
  end
  buf << "  ]"
  buf
end

font_path = ARGV[0] || "font.ttf"
out_prefix = ARGV[1] || "font_out"
px_size = (ARGV[2] || 10).to_i

json_path = "#{out_prefix}.json"
png_basename = "#{File.basename(out_prefix)}.png"
png_path = File.join(File.dirname(out_prefix), png_basename)
png_path = png_basename if File.dirname(out_prefix) == "."

kern_glyph_cap = ENV.fetch("TTF2FNT_MAX_KERN_GLYPHS", "2000").to_i

library = FFI::MemoryPointer.new(:pointer)
err = FT_Init_FreeType(library)
raise FreeType::Error.find(err) unless err == 0
lib = library.get_pointer(0)

face_ptr = FFI::MemoryPointer.new(:pointer)
err = FT_New_Face(lib, font_path, 0, face_ptr)
raise FreeType::Error.find(err) unless err == 0
face = face_ptr.get_pointer(0)

e_cm = FT_Select_Charmap(face, :FT_ENCODING_UNICODE)
warn "FT_Select_Charmap(unicode) failed: #{e_cm}" if e_cm != 0

err = FT_Set_Char_Size(face, 0, px_size * 64, 96, 96)
raise FreeType::Error.find(err) unless err == 0

face_rec = FT_FaceRec.new(face)
sm = face_rec[:size][:metrics]
line_height = (sm[:height].to_i >> 6)
base_px = (sm[:ascender].to_i >> 6)

family = face_rec[:family_name]
family = family.is_a?(FFI::Pointer) ? family.read_string : family.to_s
bold = ((face_rec[:style_flags] & FT_STYLE_FLAG_BOLD) != 0) ? 1 : 0
italic = ((face_rec[:style_flags] & FT_STYLE_FLAG_ITALIC) != 0) ? 1 : 0

codepoints = cmap_codepoints(face)
placements = []

codepoints.each do |cp|
  gid = FT_Get_Char_Index(face, cp)
  next if gid.zero?

  err = FT_Load_Char(face, cp, LOAD_FLAGS)
  raise FreeType::Error.find(err) unless err == 0

  glyph = face_rec[:glyph]
  bitmap = glyph[:bitmap]
  gw = bitmap[:width].to_i
  gh = bitmap[:rows].to_i

  unless bitmap[:pixel_mode] == FT_PIXEL_MODE_GRAY
    raise "expected GRAY bitmap (aa), got pixel_mode=#{bitmap[:pixel_mode]}"
  end

  xadv = (glyph[:advance][:x] + 32) / 64
  placements << {
    cp: cp,
    gw: gw,
    gh: gh,
    pitch: bitmap[:pitch].to_i,
    buffer: nil,
    bitmap_left: glyph[:bitmap_left],
    bitmap_top: glyph[:bitmap_top],
    xadvance: xadv
  }
end

placements.sort_by! { |g| g[:cp] }

atlas_w, atlas_h = shelf_pack!(placements, INITIAL_ATLAS_W)
img = Image.new(width: atlas_w, height: atlas_h)

placements.each do |g|
  err = FT_Load_Char(face, g[:cp], LOAD_FLAGS)
  raise FreeType::Error.find(err) unless err == 0

  glyph = face_rec[:glyph]
  bitmap = glyph[:bitmap]
  g[:pitch] = bitmap[:pitch].to_i
  g[:buffer] = bitmap[:buffer]
  g[:gw] = bitmap[:width].to_i
  g[:gh] = bitmap[:rows].to_i

  blit_glyph!(img, g, g[:atlas_x], g[:atlas_y])
end

img = trim_atlas(img, placements)
scale_w = img.width
scale_h = img.height

kernings = []
if placements.size > kern_glyph_cap
  warn "Skipping kernings: #{placements.size} glyphs > #{kern_glyph_cap} (set TTF2FNT_MAX_KERN_GLYPHS to allow)"
else
  placements.each do |a|
    placements.each do |b|
      next if a[:cp] == b[:cp]

      g1 = FT_Get_Char_Index(face, a[:cp])
      g2 = FT_Get_Char_Index(face, b[:cp])
      next if g1.zero? || g2.zero?

      v = FT_Vector.new
      err = FT_Get_Kerning(face, g1, g2, FT_KERNING_DEFAULT, v)
      next unless err == 0

      amt = (v[:x].to_f / 64.0).round.to_i
      next if amt.zero?

      kernings << { "first" => a[:cp], "second" => b[:cp], "amount" => amt }
    end
  end
end

glyphs_json = placements.map do |g|
  {
    "id" => g[:cp],
    "x"  => g[:atlas_x],
    "y"  => g[:atlas_y],
    "w"  => g[:gw],
    "h"  => g[:gh],
    "xo" => g[:bitmap_left],
    "yo" => base_px - g[:bitmap_top],
    "xa" => g[:xadvance],
  }
end

doc = {
  "atlas" => {
    "width"  => scale_w,
    "height" => scale_h,
    "image"  => png_basename
  },
  "face" => {
    "family"     => family,
    "size"       => px_size,
    "bold"       => bold,
    "italic"     => italic,
    "lineHeight" => line_height,
    "base"       => base_px,
    "padding"    => [0, 0, 0, 0],
    "spacing"    => [1, 1]
  },
  "glyphs"   => glyphs_json,
  "kernings" => kernings
}

File.binwrite(png_path, img.export)
File.write(json_path, format_font_json(doc))

FT_Done_Face(face)
FT_Done_Library(lib)

puts "wrote #{json_path}"
puts "wrote #{png_path} #{scale_w}x#{scale_h}"
puts "glyphs=#{placements.size} kernings=#{kernings.size}"
