package io.scriptdock;

import java.awt.*;
import java.awt.geom.*;
import javax.swing.Icon;

/** Dependency-free vector icons used by ScriptDock and available to embedding applications. */
public final class SwingIcons {
  private SwingIcons() {}

  public static Icon play(Color color) {
    return new ShapeIcon(color) {
      @Override
      void paint(Graphics2D graphics) {
        Path2D triangle = new Path2D.Double();
        triangle.moveTo(3, 2);
        triangle.lineTo(3, 14);
        triangle.lineTo(13, 8);
        triangle.closePath();
        graphics.fill(triangle);
      }
    };
  }

  public static Icon stop(Color color) {
    return new ShapeIcon(color) {
      @Override
      void paint(Graphics2D graphics) {
        graphics.fillRect(3, 3, 10, 10);
      }
    };
  }

  public static Icon resume(Color color) {
    return new ShapeIcon(color) {
      @Override
      void paint(Graphics2D graphics) {
        graphics.fillRect(2, 3, 2, 10);
        Path2D triangle = new Path2D.Double();
        triangle.moveTo(6, 3);
        triangle.lineTo(6, 13);
        triangle.lineTo(14, 8);
        triangle.closePath();
        graphics.fill(triangle);
      }
    };
  }

  public static Icon stepOver(Color color) {
    return new ShapeIcon(color) {
      @Override
      void paint(Graphics2D graphics) {
        Path2D triangle = new Path2D.Double();
        triangle.moveTo(1, 3);
        triangle.lineTo(1, 13);
        triangle.lineTo(9, 8);
        triangle.closePath();
        graphics.fill(triangle);
        graphics.fillRect(11, 3, 2, 10);
      }
    };
  }

    public static Icon open(Color color) {
        return new ShapeIcon(color) {
            @Override
            void paint(Graphics2D graphics) {
                graphics.setStroke(new BasicStroke(
                        1.4f,
                        BasicStroke.CAP_ROUND,
                        BasicStroke.JOIN_ROUND
                ));

                // Folder body
                Path2D folder = new Path2D.Double();
                folder.moveTo(1.5, 4);
                folder.lineTo(5.5, 4);
                folder.lineTo(7.5, 6);
                folder.lineTo(14.5, 6);
                folder.lineTo(12, 14);
                folder.lineTo(1.5, 14);
                folder.closePath();

                graphics.draw(folder);

                // Open folder flap
                Path2D flap = new Path2D.Double();
                flap.moveTo(1.5, 7);
                flap.lineTo(14, 7);
                flap.lineTo(12, 14);
                flap.lineTo(1.5, 14);
                flap.closePath();

                graphics.draw(flap);
            }
        };
    }


    public static Icon newDocument(Color color) {
    return new ShapeIcon(color) {
      @Override
      void paint(Graphics2D graphics) {
        graphics.drawRect(2, 1, 10, 14);
        graphics.setStroke(new BasicStroke(2, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        graphics.drawLine(9, 9, 15, 9);
        graphics.drawLine(12, 6, 12, 12);
      }
    };
  }

  public static Icon save(Color color) {
    return new ShapeIcon(color) {
      @Override
      void paint(Graphics2D graphics) {
        graphics.fillRect(2, 1, 12, 14);
        graphics.setColor(Color.WHITE);
        graphics.fillRect(5, 2, 6, 4);
        graphics.fillRect(5, 10, 6, 4);
      }
    };
  }

  public static Icon clear(Color color) {
    return new ShapeIcon(color) {
      @Override
      void paint(Graphics2D graphics) {
        graphics.fillRect(1, 3, 8, 2);
        graphics.fillRect(1, 7, 8, 2);
        graphics.fillRect(1, 11, 8, 2);
        graphics.setStroke(new BasicStroke(2, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        graphics.drawLine(11, 7, 15, 13);
        graphics.drawLine(15, 7, 11, 13);
      }
    };
  }

  public static Icon document(Color color) {
    return new ShapeIcon(color) {
      @Override
      void paint(Graphics2D graphics) {
        graphics.drawRect(2, 1, 11, 14);
        graphics.fillRect(4, 4, 7, 2);
        graphics.fillRect(4, 8, 7, 2);
        graphics.fillRect(4, 12, 5, 2);
      }
    };
  }

  public static Icon code(Color color) {
    return new ShapeIcon(color) {
      @Override
      void paint(Graphics2D graphics) {
        graphics.setStroke(new BasicStroke(2, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        graphics.drawLine(6, 3, 2, 8);
        graphics.drawLine(2, 8, 6, 13);
        graphics.drawLine(10, 3, 14, 8);
        graphics.drawLine(14, 8, 10, 13);
      }
    };
  }

  // Gear Icon
    public static Icon gear(Color color) {
        return new ShapeIcon(color) {
        @Override
        void paint(Graphics2D graphics) {
            graphics.setStroke(new BasicStroke(2, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            graphics.drawOval(4, 4, 8, 8);
            graphics.drawLine(8, 1, 8, 4);
            graphics.drawLine(8, 12, 8, 15);
            graphics.drawLine(1, 8, 4, 8);
            graphics.drawLine(12, 8, 15, 8);
            graphics.drawLine(3, 3, 5, 5);
            graphics.drawLine(11, 11, 13, 13);
            graphics.drawLine(3, 13, 5, 11);
            graphics.drawLine(11, 5, 13, 3);
        }
        };
    }

  // Notes icon
    public static Icon notes(Color color) {
        return new ShapeIcon(color) {
        @Override
        void paint(Graphics2D graphics) {
            graphics.fillRect(2, 1, 12, 14);
            graphics.setColor(Color.WHITE);
            graphics.fillRect(4, 3, 8, 2);
            graphics.fillRect(4, 7, 8, 2);
            graphics.fillRect(4, 11, 5, 2);
        }
        };
    }

    public static Icon macro(Color color) {
        return new ShapeIcon(color) {
            @Override
            void paint(Graphics2D graphics) {
                graphics.setStroke(new BasicStroke(
                        1.4f,
                        BasicStroke.CAP_ROUND,
                        BasicStroke.JOIN_ROUND
                ));

                // Keycap outline
                graphics.drawRoundRect(1, 2, 14, 12, 3, 3);

                // Letter M
                graphics.setFont(new Font(
                        Font.SANS_SERIF,
                        Font.BOLD,
                        9
                ));

                graphics.drawString("M", 3, 11);
            }
        };
    }

    public static Icon connect(Color color) {
        return new ShapeIcon(color) {
            @Override
            void paint(Graphics2D graphics) {
                graphics.setStroke(new BasicStroke(
                        1.8f,
                        BasicStroke.CAP_ROUND,
                        BasicStroke.JOIN_ROUND
                ));

                // Left and right connection points
                graphics.fillOval(1, 6, 4, 4);
                graphics.fillOval(11, 6, 4, 4);

                // Continuous connection
                graphics.drawLine(5, 8, 11, 8);
            }
        };
    }

    public static Icon disconnect(Color color) {
        return new ShapeIcon(color) {
            @Override
            void paint(Graphics2D graphics) {
                graphics.setStroke(new BasicStroke(
                        1.8f,
                        BasicStroke.CAP_ROUND,
                        BasicStroke.JOIN_ROUND
                ));

                // Separated connection points
                graphics.fillOval(1, 6, 4, 4);
                graphics.fillOval(11, 6, 4, 4);

                // Broken connection segments
                graphics.drawLine(5, 8, 7, 8);
                graphics.drawLine(9, 8, 11, 8);

                // Disconnect slash
                graphics.drawLine(5, 3, 11, 13);
            }
        };
    }



    private abstract static class ShapeIcon implements Icon {
    private final Color color;

    private ShapeIcon(Color color) {
      this.color = color;
    }

    @Override
    public int getIconWidth() {
      return 16;
    }

    @Override
    public int getIconHeight() {
      return 16;
    }

    @Override
    public final void paintIcon(Component component, Graphics graphics, int x, int y) {
      Graphics2D copy = (Graphics2D) graphics.create();
      try {
        copy.translate(x, y);
        copy.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        copy.setColor(component != null && !component.isEnabled() ? Color.GRAY : color);
        paint(copy);
      } finally {
        copy.dispose();
      }
    }

    abstract void paint(Graphics2D graphics);
  }
}
